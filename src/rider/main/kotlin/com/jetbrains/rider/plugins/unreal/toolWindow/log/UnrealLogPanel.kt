package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.jetbrains.rd.util.eol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.BlueprintClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.MethodReferenceHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.UnrealClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.model.*
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.MethodReference
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import java.awt.BorderLayout
import java.util.*
import javax.swing.JPanel

class UnrealLogPanel(val tabModel: String, lifetime: Lifetime, val project: Project) : SimpleToolWindowPanel(false) {
    companion object {
        private const val MAX_STORED_LOG_DATA_ITEMS = 32 * 1024
        private const val TIME_WIDTH = 29
        private const val VERBOSITY_WIDTH = 12

        private fun createConsole(project: Project, lifetime: Lifetime): ConsoleViewImpl {
            val consoleView = TextConsoleBuilderFactory.getInstance()
                    .createBuilder(project)
                    .console as ConsoleViewImpl

            lifetime.bracketIfAlive({
                // force create console ui
                consoleView.component
            }, {
                consoleView.dispose()
            })

            return consoleView

        }
    }

    private val settings: UnrealLogPanelSettings = UnrealLogPanelSettings.getInstance(project)

    private val logData: ArrayDeque<UnrealLogEvent> = ArrayDeque()
    private val consoleView: ConsoleViewImpl = createConsole(project, lifetime)

    val console: ConsoleViewImpl get() = consoleView

    private val logFilter: UnrealLogFilter = UnrealLogFilter(lifetime, settings)
    private val verbosityFilterActionGroup: UnrealLogVerbosityFilterComboBox = UnrealLogVerbosityFilterComboBox(settings)
    private val categoryFilterActionGroup: UnrealLogCategoryFilterComboBox = UnrealLogCategoryFilterComboBox(settings, logFilter)
    private val settingsActionGroup = UnrealLogSettingsActionGroup(settings)

    init {
        setContent(consoleView)
        val actionGroup = DefaultActionGroup().apply {
            addAll(consoleView.createConsoleActions().toList())
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, myVertical)

        val topGroup = DefaultActionGroup().apply {
            add(verbosityFilterActionGroup)
            add(categoryFilterActionGroup)
            add(settingsActionGroup)
        }
        val topToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, topGroup, true)

        val topPanel = JPanel(HorizontalLayout(0))
        topToolbar.targetComponent = topPanel
        topPanel.add(topToolbar.component)

        consoleView.scrollTo(0)

        consoleView.add(topPanel, BorderLayout.NORTH)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)

        logFilter.addFilterChangedListener { filter(); }

        val model = project.solution.rdRiderModel
        model.isConnectedToUnrealEditor.advise(lifetime) {
            if (it) {
                if (settings.clearOnStart) {
                    clear()
                }
            }
        }
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
        printImpl(unrealLogEvent)
    }

    private fun filter() {
        val currentScroll = consoleView.editor.scrollingModel.verticalScrollOffset
        consoleView.clear()
        // clear is not instant, it just adds an internal request in console
        // so schedule filtered content printing on next UI update after clear had been already performed
        invokeLater {
            for (logEvent in logData) {
                printImpl(logEvent)
            }
            consoleView.editor.scrollingModel.scrollVertically(currentScroll)
        }
    }

    private fun printSpaces(n: Int = 1, style: ConsoleViewContentType) {
        if (n <= 0) return
        consoleView.print(" ".repeat(n), style)
    }

    private fun printInfo(s: LogMessageInfo, style: ConsoleViewContentType) {
        if (settings.showTimestamps) {
            val timeString = s.time?.toString()
            if (timeString != null) {
                consoleView.print(timeString, style)
                printSpaces(TIME_WIDTH + 1 - timeString.length, style)
            } else {
                printSpaces(TIME_WIDTH + 1, style)
            }
        }

        if (settings.alignMessages) {
            if (settings.showVerbosity) {
                val verbosityString = s.type.toString()
                consoleView.print(verbosityString, style)
                printSpaces(VERBOSITY_WIDTH - verbosityString.length + 1, style)
            }

            val category = s.category.data
            consoleView.print(category, style)
            printSpaces(settings.categoryWidth - category.length, style)
            printSpaces(1, style)
        } else {
            if (settings.showVerbosity) {
                val verbosityString = s.type.toString()
                consoleView.print(verbosityString, style)
                consoleView.print(": ", style)
            }

            consoleView.print(s.category.data, style)
            consoleView.print(": ", style)
        }
    }

    private fun getMessageStyle(type: VerbosityType): ConsoleViewContentType {
        return when (type) {
            VerbosityType.Fatal -> ConsoleViewContentType.LOG_ERROR_OUTPUT
            VerbosityType.Error -> ConsoleViewContentType.LOG_ERROR_OUTPUT
            VerbosityType.Warning -> ConsoleViewContentType.LOG_WARNING_OUTPUT
            VerbosityType.Display -> ConsoleViewContentType.LOG_INFO_OUTPUT
            VerbosityType.Log -> ConsoleViewContentType.LOG_INFO_OUTPUT
            VerbosityType.Verbose -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
            VerbosityType.VeryVerbose -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
            else -> ConsoleViewContentType.LOG_INFO_OUTPUT
        }
    }

    private fun printImpl(unrealLogEvent: UnrealLogEvent): Boolean {
        logFilter.addCategory(unrealLogEvent.info.category.data)
        if (!logFilter.isMessageAllowed(unrealLogEvent.info)) {
            return false
        }

        val style = getMessageStyle(unrealLogEvent.info.type)

        printInfo(unrealLogEvent.info, style)

        if (unrealLogEvent.bpPathRanges.isEmpty() && unrealLogEvent.methodRanges.isEmpty()) {
            with(consoleView) {
                print(unrealLogEvent.text.data, style)
            }
            consoleView.print(eol, style)
            return true
        }

        val sortedRanges = (unrealLogEvent.bpPathRanges + unrealLogEvent.methodRanges).sortedBy { it.first }
        assert(sortedRanges.isNotEmpty())

        // collect sorted non-intersecting string ranges
        val allRanges = arrayListOf<StringRange>()
        var lastAddedRange = sortedRanges[0]
        allRanges.add(lastAddedRange)
        for (index in 1 until sortedRanges.size) {
            val curRange = sortedRanges[index]
            if (lastAddedRange.last <= curRange.first) {
                allRanges.add(curRange)
                lastAddedRange = curRange
            }
        }

        val line = unrealLogEvent.text.data
        if (allRanges.first().first > 0) {
            consoleView.print(line.substring(0, allRanges[0].first), style)
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

                consoleView.print(MethodReference.separator, style)

                val methodHyperLinkInfo = MethodReferenceHyperLinkInfo(model, methodReference)
                consoleView.printHyperlink(method, methodHyperLinkInfo)
            }
            if (rangeWithIndex.index < allRanges.size - 1 &&
                    allRanges[rangeWithIndex.index + 1].first != rangeWithIndex.value.last) {
                consoleView.print(line.substring(rangeWithIndex.value.last,
                        allRanges[rangeWithIndex.index + 1].first), style)
            }
        }
        if (allRanges.last().last < line.length) {
            consoleView.print(line.substring(allRanges.last().last), style)
        }
        consoleView.print(eol, style)
        return true
    }

    private fun addLogDataItem(item: UnrealLogEvent) {
        while (logData.size >= MAX_STORED_LOG_DATA_ITEMS)
            logData.removeFirst()
        logData.add(item)
    }
}
