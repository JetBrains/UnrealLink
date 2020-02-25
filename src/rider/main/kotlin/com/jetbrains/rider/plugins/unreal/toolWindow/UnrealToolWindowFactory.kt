package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.util.eol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.*
import com.jetbrains.rider.plugins.unreal.UnrealPane
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.ui.toolWindow.RiderOnDemandToolWindowFactory
import icons.RiderIcons

class UnrealToolWindowFactory(val project: Project)
    : RiderOnDemandToolWindowFactory<String>(project, TOOLWINDOW_ID, { it }, ::UnrealPane, { it }) {

    companion object {
        val TOOLWINDOW_ID = "unreal"
        val TITLE_ID = "unreal"
        val ACTION_PLACE = "unreal"

        fun getInstance(project: Project): UnrealToolWindowFactory = project.service()
    }

    override fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, project, true, false)

        ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)

        toolWindow.title = "unreal"
        toolWindow.setIcon(RiderIcons.Stacktrace.Stacktrace) //todo change

        return toolWindow
    }

    private fun printSpaces(n: Int = 1) {
        UnrealPane.publicConsoleView.print(" ".repeat(n), NORMAL_OUTPUT)

    }

    fun print(s: LogMessageInfo) {
        val consoleView = UnrealPane.publicConsoleView
        val timeString = s.time?.toString() ?: " ".repeat(TIME_WIDTH)
        consoleView.print(timeString, SYSTEM_OUTPUT)
        printSpaces()

        val verbosityContentType = when (s.type) {
            VerbosityType.Fatal -> ERROR_OUTPUT
            VerbosityType.Error -> ERROR_OUTPUT
            VerbosityType.Warning -> LOG_WARNING_OUTPUT
            VerbosityType.Display -> LOG_INFO_OUTPUT
            VerbosityType.Log -> LOG_INFO_OUTPUT
            VerbosityType.Verbose -> LOG_VERBOSE_OUTPUT
            VerbosityType.VeryVerbose -> LOG_DEBUG_OUTPUT
            else -> NORMAL_OUTPUT
        }

        val verbosityString = s.type.toString().take(VERBOSITY_WIDTH)
        consoleView.print(verbosityString, verbosityContentType)
        printSpaces(VERBOSITY_WIDTH - verbosityString.length + 1)

        val category = s.category.data.take(CATEGORY_WIDTH)
        consoleView.print(category, SYSTEM_OUTPUT)
        printSpaces(CATEGORY_WIDTH - category.length + 1)
    }

    internal val model = project.solution.rdRiderModel
    private val stackTraceContentType = LOG_ERROR_OUTPUT

    private fun print(message: FString) {
        with(UnrealPane.publicConsoleView) {
            print(message.data, NORMAL_OUTPUT)
        }
    }

/*
    private fun print(scriptMsg: IScriptMsg) {
        with(UnrealPane.publicConsoleView) {
            print(IScriptMsg.header, NORMAL_OUTPUT)
            println()
            when (scriptMsg) {
                is ScriptMsgException -> {
                    print(scriptMsg.message)
                }
                is ScriptMsgCallStack -> {
                    print(scriptMsg.message)
                    println()
                    print(scriptMsg.scriptCallStack)
                }
            }

        }
    }
*/

    fun print(unrealLogEvent: UnrealLogEvent) {
        showTab(TITLE_ID, Lifetime.Eternal)

        print(unrealLogEvent.info)
        print(unrealLogEvent.text)
    }

    private fun println() {
        with(UnrealPane.publicConsoleView) {
            print(eol, NORMAL_OUTPUT)
        }
    }

    fun flush() {
        println()
//        UnrealPane.publicConsoleView.flushDeferredText()
    }
}
