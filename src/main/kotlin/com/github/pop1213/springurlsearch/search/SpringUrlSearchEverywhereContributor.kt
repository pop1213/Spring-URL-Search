package com.github.pop1213.springurlsearch.search

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Processor
import com.github.pop1213.springurlsearch.services.UrlIndexService
import java.awt.Color
import javax.swing.JList
import javax.swing.ListCellRenderer

class SpringUrlSearchEverywhereContributor(private val project: Project) : SearchEverywhereContributor<UrlIndexItem> {

    override fun getSearchProviderId(): String = "SpringUrlSearchEverywhereContributor"

    override fun getGroupName(): String = "Spring Mappings URLs"

    override fun getSortWeight(): Int = 200

    override fun showInFindResults(): Boolean = false

    override fun isShownInSeparateTab(): Boolean = true

    override fun dispose() {}

    override fun isDumbAware(): Boolean = true

    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in UrlIndexItem>
    ) {
        val query = parsePattern(pattern)
        val indexService = project.service<UrlIndexService>()
        val allItems = indexService.getAllItems()

        val matched = allItems.mapNotNull { item ->
            val score = computeScore(item, query)
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }

        for ((item, _) in matched) {
            if (progressIndicator.isCanceled) break
            if (!consumer.process(item)) break
        }
    }

    override fun processSelectedItem(selected: UrlIndexItem, modifiers: Int, searchText: String): Boolean {
        ApplicationManager.getApplication().invokeLater {
            val element = selected.psiMethodPointer.element
            if (element != null && element.canNavigate()) {
                element.navigate(true)
            }
        }
        return true
    }

    override fun getDataForItem(element: UrlIndexItem, dataId: String): Any? {
        if (CommonDataKeys.PSI_ELEMENT.`is`(dataId)) {
            return element.psiMethodPointer.element
        }
        return null
    }

    private val GET_COLOR = JBColor(Color(0, 128, 0), Color(40, 167, 69))
    private val POST_COLOR = JBColor(Color(0, 102, 204), Color(50, 150, 255))
    private val PUT_COLOR = JBColor(Color(184, 134, 11), Color(253, 126, 20))
    private val DELETE_COLOR = JBColor(Color(204, 0, 0), Color(220, 53, 69))
    private val PATCH_COLOR = JBColor(Color(102, 51, 153), Color(111, 66, 193))
    private val ALL_COLOR = JBColor(Color(128, 128, 128), Color(160, 160, 160))

    private fun getMethodAttributes(method: String): SimpleTextAttributes {
        val color = when (method.uppercase()) {
            "GET" -> GET_COLOR
            "POST" -> POST_COLOR
            "PUT" -> PUT_COLOR
            "DELETE" -> DELETE_COLOR
            "PATCH" -> PATCH_COLOR
            else -> ALL_COLOR
        }
        return SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color)
    }

    override fun getElementsRenderer(): ListCellRenderer<in UrlIndexItem> {
        return object : ColoredListCellRenderer<UrlIndexItem>() {
            override fun customizeCellRenderer(
                list: JList<out UrlIndexItem>,
                value: UrlIndexItem?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value == null) return

                val methodStr = String.format("%-6s", value.method)
                append(methodStr, getMethodAttributes(value.method))

                append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append(value.url, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)

                val simpleClass = value.className.substringAfterLast('.')
                append(" ($simpleClass#${value.methodName})", SimpleTextAttributes.GRAYED_ATTRIBUTES)

                icon = AllIcons.Nodes.Method
            }
        }
    }

    data class SearchQuery(
        val httpMethod: String?,
        val pathPattern: String
    )

    private fun parsePattern(pattern: String): SearchQuery {
        val clean = pattern.trim()
        val parts = clean.split(Regex("\\s+"), 2)
        if (parts.size == 2) {
            val possibleMethod = parts[0].uppercase()
            if (possibleMethod in setOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")) {
                return SearchQuery(possibleMethod, parts[1])
            }
        }
        return SearchQuery(null, clean)
    }

    private fun computeScore(item: UrlIndexItem, query: SearchQuery): Int {
        val qPath = query.pathPattern.lowercase()
        val iUrl = item.url.lowercase()

        // 1. Check HTTP Method compatibility
        if (query.httpMethod != null) {
            if (item.method != "ALL" && item.method != query.httpMethod) {
                return 0
            }
        }

        var score = 0

        // 2. HTTP Method weight
        if (query.httpMethod != null) {
            if (item.method == query.httpMethod) {
                score += 1000
            } else if (item.method == "ALL") {
                score += 500
            }
        }

        // 3. Path matching degree: Prefix > Contains > Fuzzy
        val matchScore = when {
            iUrl == qPath -> 10000
            iUrl.startsWith(qPath) -> {
                8000 - (iUrl.length - qPath.length)
            }
            iUrl.contains(qPath) -> {
                val index = iUrl.indexOf(qPath)
                5000 - index - (iUrl.length - qPath.length)
            }
            isFuzzyMatch(iUrl, qPath) -> {
                val distance = iUrl.length - qPath.length
                1000 - distance
            }
            else -> 0
        }

        if (matchScore == 0 && qPath.isNotEmpty()) {
            return 0
        }

        return score + matchScore
    }

    private fun isFuzzyMatch(text: String, query: String): Boolean {
        if (query.isEmpty()) return true
        var textIdx = 0
        var queryIdx = 0
        val t = text.lowercase()
        val q = query.lowercase()
        while (textIdx < t.length && queryIdx < q.length) {
            if (t[textIdx] == q[queryIdx]) {
                queryIdx++
            }
            textIdx++
        }
        return queryIdx == q.length
    }
}
