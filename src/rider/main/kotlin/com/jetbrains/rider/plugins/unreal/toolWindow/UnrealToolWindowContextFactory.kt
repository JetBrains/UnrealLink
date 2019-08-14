package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.StatusBarUtil
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.ui.components.ComponentFactories
import com.jetbrains.rider.util.idea.lifetime
import icons.RiderIcons

class UnrealToolWindowContextFactory(project: Project,
                                     private val toolWindowManager: ToolWindowManager,
                                     private val host: UnrealHost)
    : LifetimedProjectComponent(project) {

    companion object {
        val TOOLWINDOW_ID = "unreal"
        val ACTION_PLACE = "unreal"
    }

    internal val context: UnrealToolWindowContext by lazy {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID)
                ?: registerToolWindow(toolWindowManager, project)

        UnrealToolWindowContext(toolWindow, ComponentFactories.getConsoleView(project, project.lifetime))
    }

    private fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, project, true, false)

        ContentManagerWatcher(toolWindow, toolWindow.contentManager)

        toolWindow.title = "unreal"
        toolWindow.icon = RiderIcons.ToolWindows.Stacktrace //todo change

        return toolWindow
    }
}