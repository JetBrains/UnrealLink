package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.daemon.HighlighterRegistrationHost
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent

class UnrealLogViewerManager(
        project: Project,
        private val highlighterRegistrationHost: HighlighterRegistrationHost
) : ConsoleFilterProvider, LifetimedProjectComponent(project) {
//    private val stackTraceFilterProvider = project.solution.stackTraceFilterProvider

    init {
//        stackTraceFilterProvider.showConsole.advise(componentLifetime) {
//            RiderStacktraceUtil.addAnalyzeExceptionTab(project, it.content, it.title)
//        }
    }

    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(UrlFilter())
    }
}