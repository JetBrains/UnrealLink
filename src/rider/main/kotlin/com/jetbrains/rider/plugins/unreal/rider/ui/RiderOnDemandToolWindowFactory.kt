package com.jetbrains.rider.plugins.unreal.rider.ui

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.util.lifetime.Lifetime

abstract class RiderOnDemandToolWindowFactory<in T : Any?> internal constructor(
    project: Project,
    val toolWindowId: String,
    private val titleFactory: (T) -> String,
    tabContentFactory: (T, Lifetime, Project) -> SimpleToolWindowPanel,
    private val keyFunction: (T) -> Any? = { it }) : AbstractProjectComponent(project) {

    //laziness here is important, we must avoid calling getToolWindow API while constructing components.
    protected val tabManager by lazy {
        RiderOnDemandTabManager(project, getToolWindow(project), titleFactory, tabContentFactory, keyFunction)
    }

    fun showTab(model: T, lt: Lifetime) {
        tabManager.showTab(model, lt)
    }

    fun getToolWindow(project: Project): ToolWindow {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        return toolWindowManager.getToolWindow(toolWindowId) ?: registerToolWindow(toolWindowManager, project)
    }

    protected abstract fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow
}

