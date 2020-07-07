package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.DocumentUtil
import com.jetbrains.rd.framework.impl.startAndAdviseSuccess
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.eol
import com.jetbrains.rider.model.*
import com.jetbrains.rider.plugins.unreal.UnrealPane
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.BlueprintClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.MethodReferenceHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.UnrealClassHyperLinkInfo
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.ui.toolWindow.RiderOnDemandToolWindowFactory
import icons.RiderIcons

import java.awt.event.ItemEvent
import java.awt.event.ItemListener

class UnrealToolWindowFactory(val project: Project)
    : RiderOnDemandToolWindowFactory<String>(project, TOOLWINDOW_ID, { it }, ::UnrealPane, { it }) {

    companion object {
        const val TOOLWINDOW_ID = "Unreal"
        const val TITLE_ID = "Unreal Editor Log"
        const val ACTION_PLACE = "unreal"

        fun getInstance(project: Project): UnrealToolWindowFactory = project.service()
    }

    private var currentCategory: String = "All"
    private var currentVerbosity: String = "All"

    override fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, false, ToolWindowAnchor.BOTTOM, project, true, false)

        ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)

        toolWindow.title = "unreal"
        toolWindow.setIcon(RiderIcons.Stacktrace.Stacktrace) //todo change

        UnrealPane.categoryCombobox.getComboBox().addItemListener(ItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                currentCategory = it.item as String
                filter()
            }
        })

        UnrealPane.verbosityCombobox.getComboBox().addItemListener(ItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                currentVerbosity = it.item as String
                filter()
            }
        })

        return toolWindow
    }

    private fun isMatchingVerbosity(valueToCheck: VerbosityType, currentSetting: String): Boolean {
        return valueToCheck.compareTo(VerbosityType.valueOf(currentSetting)) <= 0
    }

    private fun isMatchingVerbosity(valueToCheck: String, currentSetting: String): Boolean {
        return VerbosityType.valueOf(valueToCheck).compareTo(VerbosityType.valueOf(currentSetting)) <= 0
    }

    private fun filter() {
        val foldingModel = UnrealPane.currentConsoleView.editor.foldingModel as FoldingModelImpl
        foldingModel.runBatchFoldingOperation {
            for (region in foldingModel.getAllFoldRegions()) {
                foldingModel.removeFoldRegion(region)
            }
            if (currentCategory == "All" && currentVerbosity == "All") {
                return@runBatchFoldingOperation
            }
            val doc = UnrealPane.currentConsoleView.editor.document
            val text = UnrealPane.currentConsoleView.editor.document.text
            var index = 0
            var lastOffset = 0

            while (index < text.length) {
                val lineEndOffset = DocumentUtil.getLineEndOffset(index, doc)
                val verbosity = text.substring(index + TIME_WIDTH + 1, index + TIME_WIDTH + VERBOSITY_WIDTH + 1)
                val category = text.substring(index + TIME_WIDTH + VERBOSITY_WIDTH + 2, index + TIME_WIDTH + VERBOSITY_WIDTH + CATEGORY_WIDTH + 2)
                if (currentCategory != "All" && !category.trim().equals(currentCategory)) {
                    index = lineEndOffset + 1
                    continue
                }
                if (currentVerbosity != "All" && !isMatchingVerbosity(verbosity.trim(), currentVerbosity)) {
                    index = lineEndOffset + 1
                    continue
                }
                if (index != lastOffset) {
                    foldingModel.createFoldRegion(lastOffset, index, "", null, true)
                }
                lastOffset = lineEndOffset + 1
                index = lastOffset
            }
            if (lastOffset < text.length) {
                val region = foldingModel.createFoldRegion(lastOffset, text.length, "", null, true)
                region!!.setExpanded(false)
            }
        }
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
        val comboBox = UnrealPane.categoryCombobox.getComboBox()
        var exists: Boolean = false
        for (i in 0..comboBox.getItemCount()) {
            if (comboBox.getItemAt(i) == category) {
                exists = true
                break
            }
        }
        if (!exists) {
            comboBox.addItem(category)
        }

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
        var startOfLineOffset = consoleView.contentSize
        print(unrealLogEvent.info)
        print(unrealLogEvent.text)

        consoleView.flushDeferredText()
        if (!unrealLogEvent.bpPathRanges.isEmpty() || !unrealLogEvent.methodRanges.isEmpty()) {
            val startOffset = consoleView.contentSize - unrealLogEvent.text.data.length
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

        val foldingModel = UnrealPane.currentConsoleView.editor.foldingModel as FoldingModelImpl
        var existingRegion = foldingModel.getCollapsedRegionAtOffset(startOfLineOffset - 1)
        if (existingRegion == null)
            existingRegion = foldingModel.getCollapsedRegionAtOffset(startOfLineOffset - 2)
        if ((currentVerbosity != "All" && !isMatchingVerbosity(unrealLogEvent.info.type, currentVerbosity))
                || (currentCategory != "All" && unrealLogEvent.info.category.data != currentCategory)) {
            foldingModel.runBatchFoldingOperation {
                if (existingRegion != null) {
                    startOfLineOffset = existingRegion.getStartOffset()
                    foldingModel.removeFoldRegion(existingRegion)
                }
                foldingModel.createFoldRegion(startOfLineOffset, consoleView.contentSize, "", null, true)
            }
        }
        else if (existingRegion != null) {
            // expand region to cover the whole line with EOL
            foldingModel.runBatchFoldingOperation {
                val start = existingRegion.getStartOffset()
                foldingModel.removeFoldRegion(existingRegion)
                foldingModel.createFoldRegion(start, startOfLineOffset, "", null, true)
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
