package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.daemon.HighlighterRegistrationHost
import com.jetbrains.rdclient.util.idea.ProtocolSubscribedProjectComponent
import com.jetbrains.rider.plugins.unreal.filters.UnrealHeavyLogFilter
import com.jetbrains.rider.util.idea.getComponent

class UnrealLogViewerManager(
        project: Project,
        private val host: UnrealHost
) : ProtocolSubscribedProjectComponent(project), ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf<Filter>(UrlFilter(), UnrealHeavyLogFilter(project, host.model))
    }
}

class UnrealLogViewerFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<out Filter> {
        return project.getComponent<UnrealLogViewerManager>().getDefaultFilters(project)
    }
}