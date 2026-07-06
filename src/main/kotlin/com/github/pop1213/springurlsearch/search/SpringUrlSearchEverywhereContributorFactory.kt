package com.github.pop1213.springurlsearch.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.ProjectManager

class SpringUrlSearchEverywhereContributorFactory : SearchEverywhereContributorFactory<UrlIndexItem> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<UrlIndexItem> {
        val project = initEvent.project 
            ?: initEvent.dataContext.getData(CommonDataKeys.PROJECT)
            ?: ProjectManager.getInstance().openProjects.firstOrNull()
            ?: throw IllegalArgumentException("Project context could not be determined")
        return SpringUrlSearchEverywhereContributor(project)
    }
}
