package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.HorizontalLayout
import com.jetbrains.rd.util.eol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.plugins.unreal.actions.FilterCheckboxAction
import com.jetbrains.rider.plugins.unreal.actions.FilterComboAction
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.BlueprintClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.MethodReferenceHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.UnrealClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.model.*
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.MethodReference
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.ui.components.ComponentFactories
import java.awt.BorderLayout
import java.util.*
import javax.swing.JPanel

class UnrealLogPanel(val tabModel: String, lifetime: Lifetime, val project: Project) : SimpleToolWindowPanel(false) {
    companion object {
        private const val MAX_STORED_LOG_DATA_ITEMS = 32 * 1024
        private const val TIME_WIDTH = 29
        private const val VERBOSITY_WIDTH = 12
        private const val CATEGORY_WIDTH = 20
    }

    private val logData: ArrayDeque<UnrealLogEvent> = ArrayDeque()
    private val consoleView: ConsoleViewImpl = ComponentFactories.getConsoleView(project, lifetime)

    val console: ConsoleViewImpl get() = consoleView

    private val verbosityFilterActionGroup: FilterComboAction = FilterComboAction("Verbosity")
    private val categoryFilterActionGroup: FilterComboAction = FilterComboAction("Categories")
    private val timestampCheckBox: JBCheckBox = JBCheckBox("Show timestamps", false)

    var allCategoriesSelected: Boolean = true
    var timeIsShown: Boolean = false

    init {
        setContent(consoleView)
        val actionGroup = DefaultActionGroup().apply {
            addAll(consoleView.createConsoleActions().toList())
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("", actionGroup, myVertical).component

        verbosityFilterActionGroup.addItem(FilterCheckboxAction("Errors", true))
        verbosityFilterActionGroup.addItem(FilterCheckboxAction("Warnings", true))
        verbosityFilterActionGroup.addItem(FilterCheckboxAction("Messages", true))
        categoryFilterActionGroup.addItem(FilterCheckboxAction("All", true))
        val topGroup = DefaultActionGroup().apply {
            add(verbosityFilterActionGroup)
            add(categoryFilterActionGroup)
        }
        val topToolbar = ActionManager.getInstance().createActionToolbar("", topGroup, true).component

        val topPanel = JPanel(HorizontalLayout(0))
        topPanel.add(topToolbar)
        topPanel.add(timestampCheckBox)

        consoleView.scrollTo(0)

        consoleView.add(topPanel, BorderLayout.NORTH)
        setToolbar(toolbar)

        categoryFilterActionGroup.addItemListener {
            val selected = categoryFilterActionGroup.selected()
            if (allCategoriesSelected) {
                if ("All" !in selected) {
                    categoryFilterActionGroup.selectAll(false)
                    allCategoriesSelected = false
                } else if (selected.size != categoryFilterActionGroup.items().size) {
                    allCategoriesSelected = false
                    for (item in categoryFilterActionGroup.items()) {
                        if (item.getName() == "All") {
                            item.setSelected(false)
                            break
                        }
                    }
                }
            } else {
                if ("All" in selected) {
                    categoryFilterActionGroup.selectAll(true)
                    allCategoriesSelected = true
                } else if (selected.size == categoryFilterActionGroup.items().size - 1) {
                    allCategoriesSelected = true
                    for (item in categoryFilterActionGroup.items()) {
                        if (item.getName() == "All") {
                            item.setSelected(true)
                            break
                        }
                    }
                }
            }
            filter()
        }

        verbosityFilterActionGroup.addItemListener {
            filter()
        }

        timestampCheckBox.addChangeListener {
            if (timeIsShown != timestampCheckBox.isSelected) {
                filter()
                timeIsShown = !timeIsShown
            }
        }

        val model = project.solution.rdRiderModel
        model.unrealLog.advise(lifetime) { event ->
            print(event)
        }
    }

    fun clear() {
        logData.clear()
        consoleView.clear()
    }

    private fun print(unrealLogEvent: UnrealLogEvent) {
        addLogDataItem(unrealLogEvent)
        if (printImpl(unrealLogEvent)) {
            println()
        }
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
        val currentScroll = consoleView.editor.scrollingModel.verticalScrollOffset
        consoleView.clear()
        // clear is not instant, it just adds an internal request in console
        // so schedule filtered content printing on next UI update after clear had been already performed
        invokeLater {
            for (logEvent in logData) {
                if (printImpl(logEvent)) {
                    println()
                }
            }
            consoleView.editor.scrollingModel.scrollVertically(currentScroll)
        }
    }

    private fun printSpaces(n: Int = 1) {
        consoleView.print(" ".repeat(n), NORMAL_OUTPUT)
    }

    private fun print(s: LogMessageInfo) {
        if (timestampCheckBox.isSelected) {
            var timeString = s.time?.toString() ?: " ".repeat(TIME_WIDTH)
            if (timeString.length < TIME_WIDTH)
                timeString += " ".repeat(TIME_WIDTH - timeString.length)
            consoleView.print(timeString, ConsoleViewContentType.SYSTEM_OUTPUT)
            printSpaces()
        }

        val verbosityContentType = when (s.type) {
            VerbosityType.Fatal -> ConsoleViewContentType.ERROR_OUTPUT
            VerbosityType.Error -> ConsoleViewContentType.ERROR_OUTPUT
            VerbosityType.Warning -> ConsoleViewContentType.LOG_WARNING_OUTPUT
            VerbosityType.Display -> ConsoleViewContentType.LOG_INFO_OUTPUT
            VerbosityType.Log -> ConsoleViewContentType.LOG_INFO_OUTPUT
            VerbosityType.Verbose -> ConsoleViewContentType.LOG_VERBOSE_OUTPUT
            VerbosityType.VeryVerbose -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
            else -> NORMAL_OUTPUT
        }

        val verbosityString = s.type.toString().take(VERBOSITY_WIDTH)
        consoleView.print(verbosityString, verbosityContentType)
        printSpaces(VERBOSITY_WIDTH - verbosityString.length + 1)

        val category = s.category.data.take(CATEGORY_WIDTH)
        consoleView.print(category, ConsoleViewContentType.SYSTEM_OUTPUT)
        printSpaces(CATEGORY_WIDTH - category.length + 1)
    }

    private fun print(message: FString) {
        with(consoleView) {
            print(message.data, NORMAL_OUTPUT)
        }
    }

    private fun printImpl(unrealLogEvent: UnrealLogEvent): Boolean {
        val category = unrealLogEvent.info.category.data.take(CATEGORY_WIDTH)
        var exists = false
        var allSelected = true
        for (item in categoryFilterActionGroup.items()) {
            if (item.getName() == "All") {
                allSelected = item.isSelected()
            }
            if (item.getName() == category) {
                exists = true
                break
            }
        }
        if (!exists) {
            categoryFilterActionGroup.addItem(FilterCheckboxAction(category, allSelected))
        }

        val selectedCategories = categoryFilterActionGroup.selected()
        val selectedVerbosities = verbosityFilterActionGroup.selected()
        if (!isMatchingVerbosity(unrealLogEvent.info.type, selectedVerbosities) ||
                unrealLogEvent.info.category.data !in selectedCategories) {
            // skip printing this line
            return false
        }

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

        val model = project.solution.rdRiderModel

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

    private fun println() {
        with(consoleView) {
            print(eol, NORMAL_OUTPUT)
        }
    }

    private fun addLogDataItem(item: UnrealLogEvent) {
        while (logData.size >= MAX_STORED_LOG_DATA_ITEMS)
            logData.removeFirst()
        logData.add(item)
    }

}
