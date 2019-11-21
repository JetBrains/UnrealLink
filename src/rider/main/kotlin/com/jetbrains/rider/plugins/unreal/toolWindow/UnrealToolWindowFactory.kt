package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.util.eol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.UnrealLogMessage
import com.jetbrains.rider.model.VerbosityType
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.UnrealPane
import com.jetbrains.rider.plugins.unreal.rider.ui.RiderOnDemandToolWindowFactory
import icons.RiderIcons

class UnrealToolWindowFactory(val project: Project
//                              ,private val host: UnrealHost
)
    : RiderOnDemandToolWindowFactory<String>(project, TOOLWINDOW_ID, { it }, ::UnrealPane, { it }) {

    companion object {
        val TOOLWINDOW_ID = "unreal"
        val TITLE_ID = "unreal"
        val ACTION_PLACE = "unreal"
    }

    override fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, project, true, false)

        ContentManagerWatcher(toolWindow, toolWindow.contentManager)

        toolWindow.title = "unreal"
        toolWindow.icon = RiderIcons.ToolWindows.Stacktrace //todo change

        return toolWindow
    }

    private fun printSpaces(n: Int = 1) {
        UnrealPane.publicConsoleView.print(" ".repeat(n), ConsoleViewContentType.NORMAL_OUTPUT)
    }

    fun print(s: UnrealLogMessage) {
        showTab(TITLE_ID, Lifetime.Eternal)
        val consoleView = UnrealPane.publicConsoleView
        val timeString = s.time?.toString() ?: " ".repeat(TIME_WIDTH)
        consoleView.print(timeString, ConsoleViewContentType.LOG_DEBUG_OUTPUT)
        printSpaces()

        val verbosityContentType = when (s.type) {
            VerbosityType.Fatal -> ConsoleViewContentType.ERROR_OUTPUT
            VerbosityType.Error -> ConsoleViewContentType.ERROR_OUTPUT
            VerbosityType.Warning -> ConsoleViewContentType.LOG_WARNING_OUTPUT
            VerbosityType.Display -> ConsoleViewContentType.LOG_INFO_OUTPUT
            VerbosityType.Log -> ConsoleViewContentType.LOG_INFO_OUTPUT
            VerbosityType.Verbose -> ConsoleViewContentType.LOG_VERBOSE_OUTPUT
            VerbosityType.VeryVerbose -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
            else -> ConsoleViewContentType.NORMAL_OUTPUT
        }

        val verbosityString = s.type.toString().take(VERBOSITY_WIDTH)
        consoleView.print(verbosityString, verbosityContentType)
        printSpaces(VERBOSITY_WIDTH - verbosityString.length + 1)

        val category = s.category.data.take(CATEGORY_WIDTH)
        consoleView.print(category, ConsoleViewContentType.SYSTEM_OUTPUT)
        printSpaces(CATEGORY_WIDTH - category.length + 1)

        consoleView.print(s.message.data, ConsoleViewContentType.NORMAL_OUTPUT)
        consoleView.print(eol, ConsoleViewContentType.NORMAL_OUTPUT)
        consoleView.flushDeferredText()
    }

}