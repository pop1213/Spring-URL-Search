package com.github.pop1213.springurlsearch.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.github.pop1213.springurlsearch.services.UrlIndexService

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val indexService = project.service<UrlIndexService>()
        DumbService.getInstance(project).runWhenSmart {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Building Spring URL Index", true) {
                override fun run(indicator: ProgressIndicator) {
                    indexService.rebuildIndex(indicator)
                }
            })
        }
    }
}