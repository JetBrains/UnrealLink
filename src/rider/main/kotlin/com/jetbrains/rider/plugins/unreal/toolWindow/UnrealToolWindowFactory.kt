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
import com.jetbrains.rider.plugins.unreal.actions.*
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

    var allCetegoriesSelected: Boolean = true

    override fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, false, ToolWindowAnchor.BOTTOM, project, true, false)

        ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)

        toolWindow.title = "unreal"
        toolWindow.setIcon(RiderIcons.Stacktrace.Stacktrace) //todo change

        UnrealPane.categoryFilterActionGroup.addItemListener({
            val selected = UnrealPane.categoryFilterActionGroup.selected()
            if (allCetegoriesSelected) {
                if (!("All" in selected)) {
                    UnrealPane.categoryFilterActionGroup.selectAll(false)
                    allCetegoriesSelected = false
                } else if (selected.size != UnrealPane.categoryFilterActionGroup.items().size) {
                    allCetegoriesSelected = false
                    for (item in UnrealPane.categoryFilterActionGroup.items()) {
                        if (item.getName() == "All") {
                            item.setSelected(false)
                            break;
                        }
                    }
                }
            } else {
                if ("All" in selected) {
                    UnrealPane.categoryFilterActionGroup.selectAll(true)
                    allCetegoriesSelected = true
                } else if (selected.size == UnrealPane.categoryFilterActionGroup.items().size - 1) {
                    allCetegoriesSelected = true
                    for (item in UnrealPane.categoryFilterActionGroup.items()) {
                        if (item.getName() == "All") {
                            item.setSelected(true)
                            break;
                        }
                    }
                }
            }
            filter()
        })

        UnrealPane.verbosityFilterActionGroup.addItemListener({
            filter()
        })

        UnrealPane.timestampCheckBox.addChangeListener { event ->
            filter()
        }

        return toolWindow
    }

    private fun isMatchingVerbosity(valueToCheck: VerbosityType, currentList: List<String>): Boolean {
        if (currentList.isEmpty()) {
            return false
        }

        if (valueToCheck.compareTo(VerbosityType.Error) <= 0)
            return "Errors" in currentList
        if (valueToCheck == VerbosityType.Warning)
            return "Warnings" in currentList

        return "Messages" in currentList
    }

    private fun isMatchingVerbosity(valueToCheck: String, currentList: List<String>): Boolean {
        if (currentList.isEmpty()) {
            return false
        }

        val value = VerbosityType.valueOf(valueToCheck)
        if (value.compareTo(VerbosityType.Error) <= 0)
            return "Errors" in currentList
        if (value == VerbosityType.Warning)
            return "Warnings" in currentList

        return "Messages" in currentList
    }

    private fun filter() {
        val foldingModel = UnrealPane.currentConsoleView.editor.foldingModel as FoldingModelImpl
        foldingModel.runBatchFoldingOperation {
            for (region in foldingModel.getAllFoldRegions()) {
                foldingModel.removeFoldRegion(region)
            }
            val selectedCategories = UnrealPane.categoryFilterActionGroup.selected()
            val selectedVerbosities = UnrealPane.verbosityFilterActionGroup.selected()
            if ("All" in selectedCategories && selectedVerbosities.size == 3 && UnrealPane.timestampCheckBox.isSelected()) {
                return@runBatchFoldingOperation
            }
            val doc = UnrealPane.currentConsoleView.editor.document
            val chars: CharSequence = doc.getCharsSequence()
            val text = UnrealPane.currentConsoleView.editor.document.text
            var index = 0
            var lastOffset = 0

            val showTimestamp = UnrealPane.timestampCheckBox.isSelected()

            while (index < text.length) {
                val lineEndOffset = DocumentUtil.getLineEndOffset(index, doc)
                val verbosity = text.substring(index + TIME_WIDTH + 1, index + TIME_WIDTH + VERBOSITY_WIDTH + 1)
                val category = text.substring(index + TIME_WIDTH + VERBOSITY_WIDTH + 2, index + TIME_WIDTH + VERBOSITY_WIDTH + CATEGORY_WIDTH + 2)
                if (!(category.trim() in selectedCategories)) {
                    index = lineEndOffset + 1
                    continue
                }
                if (!isMatchingVerbosity(verbosity.trim(), selectedVerbosities)) {
                    index = lineEndOffset + 1
                    continue
                }
                if (!showTimestamp) {
                    foldingModel.createFoldRegion(lastOffset, index + TIME_WIDTH + 1, "", null, true)
                } else if (lastOffset != index) {
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
        var timeString = s.time?.toString() ?: " ".repeat(TIME_WIDTH)
        if (timeString.length < TIME_WIDTH)
            timeString = timeString + " ".repeat(TIME_WIDTH - timeString.length)
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
        var exists: Boolean = false
        var allSelected: Boolean = true
        for (item in UnrealPane.categoryFilterActionGroup.items()) {
            if (item.getName() == "All") {
                allSelected = item.isSelected()
            }
            if (item.getName() == category) {
                exists = true
                break;
            }
        }
        if (!exists) {
            UnrealPane.categoryFilterActionGroup.addItem(FilterCheckboxAction(category, allSelected))
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
        print(unrealLogEvent.info)
        print(unrealLogEvent.text)

        consoleView.flushDeferredText()
        val startOffset = consoleView.contentSize - unrealLogEvent.text.data.length
        var startOfLineOffset = startOffset - (TIME_WIDTH + VERBOSITY_WIDTH + CATEGORY_WIDTH + 3)
        if (!unrealLogEvent.bpPathRanges.isEmpty() || !unrealLogEvent.methodRanges.isEmpty()) {
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

        val selectedCategories = UnrealPane.categoryFilterActionGroup.selected()
        val selectedVerbosities = UnrealPane.verbosityFilterActionGroup.selected()
        val showTimestamp = UnrealPane.timestampCheckBox.isSelected()
        if (!isMatchingVerbosity(unrealLogEvent.info.type, selectedVerbosities) ||
                !(unrealLogEvent.info.category.data in selectedCategories) ||
                !showTimestamp) {
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
                if (showTimestamp)
                    foldingModel.createFoldRegion(start, startOfLineOffset, "", null, true)
                else
                    foldingModel.createFoldRegion(start, startOfLineOffset + TIME_WIDTH + 1, "", null, true)
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
