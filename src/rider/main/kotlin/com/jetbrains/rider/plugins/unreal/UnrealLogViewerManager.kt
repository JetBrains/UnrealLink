package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.daemon.HighlighterRegistrationHost
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.plugins.unreal.filters.UnrealHeavyLogFilter
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.util.idea.getComponent

class UnrealLogViewerManager(
        project: Project,
        private val highlighterRegistrationHost: HighlighterRegistrationHost
) : ConsoleFilterProvider, LifetimedProjectComponent(project) {
//    private val stackTraceFilterProvider = project.solution.stackTraceFilterProvider

    val riderModel = project.solution.rdRiderModel

    init {
//        stackTraceFilterProvider.showConsole.advise(componentLifetime) {
//            RiderStacktraceUtil.addAnalyzeExceptionTab(project, it.content, it.title)
//        }
    }

    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf<Filter>(UrlFilter(), UnrealHeavyLogFilter(project, highlighterRegistrationHost, riderModel.isBlueprint, riderModel.navigate))
    }
}

class UnrealLogViewerFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<out Filter> {
        return project.getComponent<UnrealLogViewerManager>().getDefaultFilters(project)
    }
}