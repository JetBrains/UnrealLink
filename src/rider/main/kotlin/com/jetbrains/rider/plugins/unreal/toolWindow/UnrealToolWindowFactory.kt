package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.eol
import com.jetbrains.rider.plugins.unreal.UnrealPane
import com.jetbrains.rider.plugins.unreal.actions.FilterCheckboxAction
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.BlueprintClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.MethodReferenceHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.UnrealClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.model.*
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.MethodReference
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
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

    var allCategoriesSelected: Boolean = true
    var timeIsShown: Boolean = false

    override fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, false, ToolWindowAnchor.BOTTOM, project, true, false)

        ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)

        toolWindow.title = "Unreal"
        toolWindow.setIcon(RiderIcons.Stacktrace.Stacktrace) //todo change

        UnrealPane.categoryFilterActionGroup.addItemListener {
            val selected = UnrealPane.categoryFilterActionGroup.selected()
            if (allCategoriesSelected) {
                if ("All" !in selected) {
                    UnrealPane.categoryFilterActionGroup.selectAll(false)
                    allCategoriesSelected = false
                } else if (selected.size != UnrealPane.categoryFilterActionGroup.items().size) {
                    allCategoriesSelected = false
                    for (item in UnrealPane.categoryFilterActionGroup.items()) {
                        if (item.getName() == "All") {
                            item.setSelected(false)
                            break
                        }
                    }
                }
            } else {
                if ("All" in selected) {
                    UnrealPane.categoryFilterActionGroup.selectAll(true)
                    allCategoriesSelected = true
                } else if (selected.size == UnrealPane.categoryFilterActionGroup.items().size - 1) {
                    allCategoriesSelected = true
                    for (item in UnrealPane.categoryFilterActionGroup.items()) {
                        if (item.getName() == "All") {
                            item.setSelected(true)
                            break
                        }
                    }
                }
            }
            filter()
        }

        UnrealPane.verbosityFilterActionGroup.addItemListener {
            filter()
        }

        UnrealPane.timestampCheckBox.addChangeListener {
            if (timeIsShown != UnrealPane.timestampCheckBox.isSelected) {
                filter()
                timeIsShown = !timeIsShown
            }
        }

        return toolWindow
    }

    private fun isMatchingVerbosity(valueToCheck: VerbosityType, currentList: List<String>): Boolean {
        if (currentList.isEmpty()) {
            return false
        }

        if (valueToCheck <= VerbosityType.Error)
            return "Errors" in currentList
        if (valueToCheck == VerbosityType.Warning)
            return "Warnings" in currentList

        return "Messages" in currentList
    }

    private fun filter() {
        val currentScroll = UnrealPane.currentConsoleView.editor.scrollingModel.verticalScrollOffset
        UnrealPane.currentConsoleView.clear()
        UnrealPane.currentConsoleView.waitAllRequests()
        for (logEvent in UnrealPane.logData) {
            if (printImpl(logEvent)) {
                println()
            }
        }
        // Flush not to show an empty window for some msecs
        UnrealPane.currentConsoleView.flushDeferredText()
        UnrealPane.currentConsoleView.editor.scrollingModel.scrollVertically(currentScroll)
    }

    private fun printSpaces(n: Int = 1) {
        UnrealPane.currentConsoleView.print(" ".repeat(n), NORMAL_OUTPUT)
    }

    private fun print(s: LogMessageInfo) {
        val consoleView = UnrealPane.currentConsoleView

        if (UnrealPane.timestampCheckBox.isSelected) {
            var timeString = s.time?.toString() ?: " ".repeat(TIME_WIDTH)
            if (timeString.length < TIME_WIDTH)
                timeString += " ".repeat(TIME_WIDTH - timeString.length)
            consoleView.print(timeString, SYSTEM_OUTPUT)
            printSpaces()
        }

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

    private fun print(message: FString) {
        with(UnrealPane.currentConsoleView) {
            print(message.data, NORMAL_OUTPUT)
        }
    }

    private fun printImpl(unrealLogEvent: UnrealLogEvent): Boolean {
        val category = unrealLogEvent.info.category.data.take(CATEGORY_WIDTH)
        var exists = false
        var allSelected = true
        for (item in UnrealPane.categoryFilterActionGroup.items()) {
            if (item.getName() == "All") {
                allSelected = item.isSelected()
            }
            if (item.getName() == category) {
                exists = true
                break
            }
        }
        if (!exists) {
            UnrealPane.categoryFilterActionGroup.addItem(FilterCheckboxAction(category, allSelected))
        }

        val selectedCategories = UnrealPane.categoryFilterActionGroup.selected()
        val selectedVerbosities = UnrealPane.verbosityFilterActionGroup.selected()
        if (!isMatchingVerbosity(unrealLogEvent.info.type, selectedVerbosities) ||
                unrealLogEvent.info.category.data !in selectedCategories) {
            // skip printing this line
            return false
        }

        val consoleView = UnrealPane.currentConsoleView
        print(unrealLogEvent.info)

        if (unrealLogEvent.bpPathRanges.isEmpty() && unrealLogEvent.methodRanges.isEmpty()) {
            print(unrealLogEvent.text)
            return true
        }

        val allRanges = (unrealLogEvent.bpPathRanges + unrealLogEvent.methodRanges).sortedBy { it.first }
        val line = unrealLogEvent.text.data
        if (allRanges.first().first > 0) {
            consoleView.print(line.substring(0, allRanges[0].first), NORMAL_OUTPUT)
        }

        for (rangeWithIndex in allRanges.withIndex()) {
            val match = line.substring(rangeWithIndex.value.first, rangeWithIndex.value.last)
            if (rangeWithIndex.value in unrealLogEvent.bpPathRanges) {
                val hyperLinkInfo = BlueprintClassHyperLinkInfo(model.openBlueprint, BlueprintReference(FString(match)))
                consoleView.printHyperlink(match, hyperLinkInfo)
            } else {
                val (`class`, method) = match.split(MethodReference.separator)
                val methodReference = MethodReference(UClass(FString(`class`)), FString(method))

                val classHyperLinkInfo = UnrealClassHyperLinkInfo(model, methodReference, UClass(FString(`class`)))
                consoleView.printHyperlink(`class`, classHyperLinkInfo)

                consoleView.print(MethodReference.separator, NORMAL_OUTPUT)

                val methodHyperLinkInfo = MethodReferenceHyperLinkInfo(model, methodReference)
                consoleView.printHyperlink(method, methodHyperLinkInfo)
            }
            if (rangeWithIndex.index < allRanges.size - 1 &&
                    allRanges[rangeWithIndex.index + 1].first != rangeWithIndex.value.last) {
                consoleView.print(line.substring(rangeWithIndex.value.last,
                        allRanges[rangeWithIndex.index + 1].first), NORMAL_OUTPUT)
            }
        }
        if (allRanges.last().last < line.length) {
            consoleView.print(line.substring(allRanges.last().last), NORMAL_OUTPUT)
        }
        return true
    }

    fun print(unrealLogEvent: UnrealLogEvent) {
        UnrealPane.addLogDataItem(unrealLogEvent)
        if (printImpl(unrealLogEvent)) {
            println()
        }
    }

    fun showTabForNewSession() {
        showTab(TITLE_ID, project.lifetime)
    }

    private fun println() {
        with(UnrealPane.currentConsoleView) {
            print(eol, NORMAL_OUTPUT)
        }
    }
}
