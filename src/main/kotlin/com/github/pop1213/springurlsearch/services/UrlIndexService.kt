package com.github.pop1213.springurlsearch.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Alarm
import com.github.pop1213.springurlsearch.search.UrlIndexItem
import org.jetbrains.uast.*
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class UrlIndexService(private val project: Project) {

    private val index = ConcurrentHashMap<String, List<UrlIndexItem>>()
    private val pendingFiles = ConcurrentHashMap.newKeySet<VirtualFile>()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

    init {
        // Register PSI listener for real-time incremental update
        PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
            override fun childrenChanged(event: PsiTreeChangeEvent) {
                event.file?.virtualFile?.let { queueFileReindex(it) }
            }

            override fun childAdded(event: PsiTreeChangeEvent) {
                event.file?.virtualFile?.let { queueFileReindex(it) }
            }

            override fun childRemoved(event: PsiTreeChangeEvent) {
                event.file?.virtualFile?.let { queueFileReindex(it) }
            }

            override fun childReplaced(event: PsiTreeChangeEvent) {
                event.file?.virtualFile?.let { queueFileReindex(it) }
            }
        }, project)

        // Register VFS listener for file delete, move, create events
        project.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    val file = event.file
                    if (event is VFileDeleteEvent || event is VFileMoveEvent) {
                        removeFileFromIndex(event.path)
                    }
                    if (file != null && isValidFile(file)) {
                        queueFileReindex(file)
                    }
                }
            }
        })
    }

    fun getAllItems(): List<UrlIndexItem> {
        return index.values.flatten()
    }

    private fun isValidFile(file: VirtualFile?): Boolean {
        if (file == null || !file.isValid) return false
        val ext = file.extension
        return ext == "java" || ext == "kt"
    }

    fun removeFileFromIndex(filePath: String) {
        index.remove(filePath)
    }

    fun queueFileReindex(file: VirtualFile) {
        if (!isValidFile(file)) return
        pendingFiles.add(file)
        alarm.cancelAllRequests()
        alarm.addRequest({
            val filesToProcess = pendingFiles.toList()
            pendingFiles.clear()
            for (f in filesToProcess) {
                reindexFile(f)
            }
        }, 1000) // Debounce 1000ms
    }

    private fun reindexFile(file: VirtualFile) {
        if (!file.isValid) {
            removeFileFromIndex(file.path)
            return
        }
        removeFileFromIndex(file.path)
        ApplicationManager.getApplication().runReadAction {
            if (!project.isDisposed) {
                val psiFile = PsiManager.getInstance(project).findFile(file)
                if (psiFile != null) {
                    scanPsiFile(psiFile)
                }
            }
        }
    }

    fun rebuildIndex(indicator: ProgressIndicator? = null) {
        index.clear()
        val scope = GlobalSearchScope.projectScope(project)
        val javaFileType = FileTypeManager.getInstance().getFileTypeByExtension("java")
        val ktFileType = FileTypeManager.getInstance().findFileTypeByName("Kotlin")

        val virtualFiles = mutableListOf<VirtualFile>()

        ApplicationManager.getApplication().runReadAction {
            if (!project.isDisposed) {
                val javaFiles = FileTypeIndex.getFiles(javaFileType, scope)
                virtualFiles.addAll(javaFiles)

                if (ktFileType != null) {
                    val ktFiles = FileTypeIndex.getFiles(ktFileType, scope)
                    virtualFiles.addAll(ktFiles)
                }
            }
        }

        var count = 0
        val total = virtualFiles.size

        for (file in virtualFiles) {
            if (indicator != null) {
                if (indicator.isCanceled) return
                indicator.text2 = "Scanning ${file.name} (${++count}/$total)"
                indicator.fraction = count.toDouble() / total
            }

            ApplicationManager.getApplication().runReadAction {
                if (!project.isDisposed && file.isValid) {
                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    if (psiFile != null) {
                        scanPsiFile(psiFile)
                    }
                }
            }
        }
        thisLogger().info("Spring URL Index built. Found ${getAllItems().size} mappings.")
    }

    private fun scanPsiFile(psiFile: PsiFile) {
        val uFile = psiFile.toUElementOfType<UFile>() ?: return
        val items = mutableListOf<UrlIndexItem>()
        for (uClass in uFile.classes) {
            val classAnnotations = uClass.uAnnotations
            val isController = classAnnotations.any { ann ->
                val qName = ann.qualifiedName
                qName == "org.springframework.stereotype.Controller" ||
                qName == "Controller" ||
                qName == "org.springframework.web.bind.annotation.RestController" ||
                qName == "RestController"
            }
            if (!isController) continue

            val classMappingAnn = classAnnotations.find {
                it.qualifiedName == "org.springframework.web.bind.annotation.RequestMapping" ||
                it.qualifiedName == "RequestMapping"
            }
            val classPaths = if (classMappingAnn != null) {
                val paths = extractStrings(classMappingAnn.findAttributeValue("value")) +
                            extractStrings(classMappingAnn.findAttributeValue("path"))
                if (paths.isEmpty()) listOf("") else paths.distinct()
            } else {
                listOf("")
            }

            for (uMethod in uClass.methods) {
                val psiMethod = uMethod.sourcePsi as? PsiMethod ?: uMethod.javaPsi ?: continue
                val methodAnnotations = uMethod.uAnnotations
                for (annotation in methodAnnotations) {
                    val qName = annotation.qualifiedName ?: continue
                    val httpMethods = if (qName == "org.springframework.web.bind.annotation.RequestMapping" || qName == "RequestMapping") {
                        getHttpMethodsFromRequestMapping(annotation)
                    } else {
                        val m = getHttpMethodFromAnnotation(qName)
                        if (m != null) listOf(m) else null
                    }

                    if (httpMethods == null) continue

                    val methodPaths = extractStrings(annotation.findAttributeValue("value")) +
                                      extractStrings(annotation.findAttributeValue("path"))
                    val finalMethodPaths = if (methodPaths.isEmpty()) listOf("") else methodPaths.distinct()

                    for (classPath in classPaths) {
                        for (methodPath in finalMethodPaths) {
                            val combinedPath = combinePaths(classPath, methodPath)
                            for (httpMethod in httpMethods) {
                                val item = UrlIndexItem(
                                    url = combinedPath,
                                    method = httpMethod,
                                    psiMethodPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiMethod),
                                    className = uClass.qualifiedName ?: uClass.name ?: "Unknown",
                                    methodName = uMethod.name
                                )
                                items.add(item)
                            }
                        }
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
            index[psiFile.virtualFile.path] = items
        }
    }

    private fun getHttpMethodFromAnnotation(qName: String): String? {
        return when (qName) {
            "org.springframework.web.bind.annotation.GetMapping", "GetMapping" -> "GET"
            "org.springframework.web.bind.annotation.PostMapping", "PostMapping" -> "POST"
            "org.springframework.web.bind.annotation.PutMapping", "PutMapping" -> "PUT"
            "org.springframework.web.bind.annotation.DeleteMapping", "DeleteMapping" -> "DELETE"
            "org.springframework.web.bind.annotation.PatchMapping", "PatchMapping" -> "PATCH"
            else -> null
        }
    }

    private fun getHttpMethodsFromRequestMapping(annotation: UAnnotation): List<String> {
        val methodExpr = annotation.findAttributeValue("method") ?: return listOf("ALL")

        fun parseSingleMethod(expr: UExpression): String? {
            val text = expr.asSourceString()
            val lastPart = text.substringAfterLast('.').trim()
            if (lastPart in setOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")) {
                return lastPart
            }
            return null
        }

        val exprs = if (methodExpr is UCallExpression) {
            methodExpr.valueArguments
        } else {
            listOf(methodExpr)
        }

        val methods = exprs.mapNotNull { parseSingleMethod(it) }
        return if (methods.isEmpty()) listOf("ALL") else methods
    }

    private fun combinePaths(classPath: String, methodPath: String): String {
        val p1 = classPath.trim().removeSuffix("/")
        val p2 = methodPath.trim().removePrefix("/")
        val combined = when {
            p1.isEmpty() && p2.isEmpty() -> "/"
            p1.isEmpty() -> "/$p2"
            p2.isEmpty() -> if (p1.startsWith("/")) p1 else "/$p1"
            else -> {
                val start = if (p1.startsWith("/")) p1 else "/$p1"
                "$start/$p2"
            }
        }
        return combined.replace(Regex("/{2,}"), "/")
    }

    private fun extractStrings(expression: UExpression?): List<String> {
        if (expression == null) return emptyList()
        val evaluated = expression.evaluate()
        if (evaluated is String) return listOf(evaluated)
        if (evaluated is Array<*>) return evaluated.mapNotNull { it as? String }
        if (evaluated is Collection<*>) return evaluated.mapNotNull { it as? String }

        if (expression is UCallExpression) {
            return expression.valueArguments.flatMap { extractStrings(it) }
        }

        val text = expression.asSourceString().trim()
        if (text.startsWith("\"") && text.endsWith("\"")) {
            return listOf(text.substring(1, text.length - 1))
        }
        return emptyList()
    }
}
