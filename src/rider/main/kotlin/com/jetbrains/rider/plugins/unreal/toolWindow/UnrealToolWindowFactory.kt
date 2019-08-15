package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.UnrealLogMessage
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.UnrealPane
import com.jetbrains.rider.plugins.unreal.rider.ui.RiderOnDemandToolWindowFactory
import icons.RiderIcons

class UnrealToolWindowFactory(val project: Project,
                              private val host: UnrealHost)
    : RiderOnDemandToolWindowFactory<Any>(project, TOOLWINDOW_ID, { it.toString() }, ::UnrealPane, {}) {

    companion object {
        val TOOLWINDOW_ID = "unreal"
        val ACTION_PLACE = "unreal"
    }

    override fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, project, true, false)

        ContentManagerWatcher(toolWindow, toolWindow.contentManager)

        toolWindow.title = "unreal"
        toolWindow.icon = RiderIcons.ToolWindows.Stacktrace //todo change

        return toolWindow
    }

    fun print(s : UnrealLogMessage) {
        showTab(Any(), Lifetime.Eternal)
        UnrealPane.publicConsoleView.print(s.type.toString(), ConsoleViewContentType.LOG_ERROR_OUTPUT)
        UnrealPane.publicConsoleView.print(s.message.data, ConsoleViewContentType.LOG_WARNING_OUTPUT)
        UnrealPane.publicConsoleView.print(s.category.data, ConsoleViewContentType.LOG_INFO_OUTPUT)
        s.time?.let {
            UnrealPane.publicConsoleView.print(it.toString(), ConsoleViewContentType.LOG_DEBUG_OUTPUT)
        }
    }

}