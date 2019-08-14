package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.jetbrains.rider.stacktrace.RiderStackTraceAnalyzerManager
import com.jetbrains.rider.util.idea.getComponent

class UnrealFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return project.getComponent<UnrealLogViewerManager>().getDefaultFilters(project)
    }
}