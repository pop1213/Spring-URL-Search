package com.github.pop1213.springurlsearch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.github.pop1213.springurlsearch.services.UrlIndexService

class RefreshSpringMappingsAction : AnAction(
    "Refresh Spring Mappings",
    "Manually trigger a re-scan of Spring URL mappings",
    AllIcons.Actions.Refresh
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Rebuilding Spring URL Index") {
            override fun run(indicator: ProgressIndicator) {
                val service = project.service<UrlIndexService>()
                service.rebuildIndex(indicator)
            }
        })
    }
}
