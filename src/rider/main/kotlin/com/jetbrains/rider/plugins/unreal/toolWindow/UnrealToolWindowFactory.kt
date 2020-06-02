package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.framework.impl.startAndAdviseSuccess
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.eol
import com.jetbrains.rider.model.*
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.BlueprintClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.MethodReferenceHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.UnrealClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.UnrealPane
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.ui.toolWindow.RiderOnDemandToolWindowFactory
import icons.RiderIcons

class UnrealToolWindowFactory(val project: Project)
    : RiderOnDemandToolWindowFactory<String>(project, TOOLWINDOW_ID, { it }, ::UnrealPane, { it }) {

    companion object {
        const val TOOLWINDOW_ID = "Unreal"
        const val TITLE_ID = "Unreal Editor Log"
        const val ACTION_PLACE = "unreal"

        fun getInstance(project: Project): UnrealToolWindowFactory = project.service()
    }

    override fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, false, ToolWindowAnchor.BOTTOM, project, true, false)

        ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)

        toolWindow.title = "unreal"
        toolWindow.setIcon(RiderIcons.Stacktrace.Stacktrace) //todo change

        return toolWindow
    }

    private fun printSpaces(n: Int = 1) {
        UnrealPane.currentConsoleView.print(" ".repeat(n), NORMAL_OUTPUT)

    }

    fun print(s: LogMessageInfo) {
        val consoleView = UnrealPane.currentConsoleView
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
        with(UnrealPane.currentConsoleView) {
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
        val consoleView = UnrealPane.currentConsoleView
        print(unrealLogEvent.info)
        val startOffset = consoleView.contentSize
        print(unrealLogEvent.text)
        if (!unrealLogEvent.bpPathRanges.isEmpty() || !unrealLogEvent.methodRanges.isEmpty())
            consoleView.flushDeferredText()
        for (range in unrealLogEvent.bpPathRanges) {
            val match = unrealLogEvent.text.data.substring(range.first, range.last)
            val hyperLinkInfo = BlueprintClassHyperLinkInfo(model.openBlueprint, BlueprintReference(FString(match)))
            consoleView.hyperlinks.createHyperlink(startOffset + range.first, startOffset + range.last, null, hyperLinkInfo)
        }
        for (range in unrealLogEvent.methodRanges) {
            val match = unrealLogEvent.text.data.substring(range.first, range.last)
            val (`class`, method) = match.split(MethodReference.separator)
            val methodReference = MethodReference(UClass(FString(`class`)), FString(method))
            model.isMethodReference.startAndAdviseSuccess(methodReference) {
                if (it) {
                    run {
                        val first = startOffset + range.first
                        val last = startOffset + range.first + `class`.length
                        val linkInfo = UnrealClassHyperLinkInfo(model.navigateToClass, UClass(FString(`class`)))
                        consoleView.hyperlinks.createHyperlink(first, last, null, linkInfo)
                    }
                    run {
                        val linkInfo = MethodReferenceHyperLinkInfo(model.navigateToMethod, methodReference)
                        val first = startOffset + range.last - method.length
                        val last = startOffset + range.last
                        consoleView.hyperlinks.createHyperlink(first, last, null, linkInfo)
                    }
                }
            }
        }
    }

    fun showTabForNewSession() {
        showTab("$TITLE_ID", project.lifetime)
    }

    private fun println() {
        with(UnrealPane.currentConsoleView) {
            print(eol, NORMAL_OUTPUT)
        }
    }

    fun flush() {
        println()
//        UnrealPane.publicConsoleView.flushDeferredText()
    }
}
