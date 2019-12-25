package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.util.eol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.*
import com.jetbrains.rider.plugins.unreal.UnrealPane
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.BlueprintClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.BlueprintFunctionHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.rider.ui.RiderOnDemandToolWindowFactory
import com.jetbrains.rider.projectView.solution
import icons.RiderIcons

class UnrealToolWindowFactory(val project: Project
//                              ,private val host: UnrealHost
) : RiderOnDemandToolWindowFactory<String>(project, TOOLWINDOW_ID, { it }, ::UnrealPane, { it }) {

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
        UnrealPane.publicConsoleView.print(" ".repeat(n), NORMAL_OUTPUT)

    }

    fun print(s: LogMessageInfo) {
        showTab(TITLE_ID, Lifetime.Eternal)

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

    fun print(scriptCallStack: IScriptCallStack) {
        with(UnrealPane.publicConsoleView) {
            when (scriptCallStack) {
                is EmptyScriptCallStack -> {
                    print(EmptyScriptCallStack.message, stackTraceContentType)
                }
                is ScriptCallStack -> {
                    print(IScriptCallStack.header, stackTraceContentType)
                    println()
                    for (frame in scriptCallStack.frames) {
                        print(frame.header.data, stackTraceContentType)

                        val blueprintClassHyperLinkInfo = BlueprintClassHyperLinkInfo(model.navigateToBlueprintClass, frame.blueprintFunction.`class`)
                        printHyperlink("${frame.blueprintFunction.`class`.pathName}", blueprintClassHyperLinkInfo)

                        print(BlueprintFunction.separator, stackTraceContentType)

                        val blueprintFunctionHyperLinkInfo = BlueprintFunctionHyperLinkInfo(model.navigateToBlueprintFunction, frame.blueprintFunction)
                        printHyperlink("${frame.blueprintFunction.methodName}", blueprintFunctionHyperLinkInfo)

                        println()
                    }
                }
                is UnableToDisplayScriptCallStack -> {
                    print(UnableToDisplayScriptCallStack.message, stackTraceContentType)
                }
            }
        }
    }

    fun print(message: FString) {
        with(UnrealPane.publicConsoleView) {
            print(message.data, NORMAL_OUTPUT)
        }
    }

    fun print(scriptMsg: IScriptMsg) {
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
