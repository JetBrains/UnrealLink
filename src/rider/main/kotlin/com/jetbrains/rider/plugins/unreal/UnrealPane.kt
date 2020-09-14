package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ide.actions.NextOccurenceToolbarAction
import com.intellij.ide.actions.PreviousOccurenceToolbarAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.HorizontalLayout
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.UnrealLogEvent
import com.jetbrains.rider.plugins.unreal.actions.FilterCheckboxAction
import com.jetbrains.rider.plugins.unreal.actions.FilterComboAction
import com.jetbrains.rider.ui.components.ComponentFactories
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane

class UnrealPane(val model: Any, lifetime: Lifetime, val project: Project) : SimpleToolWindowPanel(false) {
    private val consoleView: ConsoleViewImpl = ComponentFactories.getConsoleView(project, lifetime)
    private val timeConsoleView: ConsoleViewImpl = ComponentFactories.getConsoleView(project, lifetime)

    companion object {
        lateinit var currentConsoleView : ConsoleViewImpl
        lateinit var currentTimeConsoleView : ConsoleViewImpl
        val verbosityFilterActionGroup: FilterComboAction = FilterComboAction("Verbosity")
        val categoryFilterActionGroup: FilterComboAction = FilterComboAction("Categories")
        val timestampCheckBox: JBCheckBox = JBCheckBox("Show timestamps", false)

        val logData: ArrayList<UnrealLogEvent> = arrayListOf()
        var logSize = 0
        var filteringInProgress = false
    }

    init {
        currentTimeConsoleView = timeConsoleView
        (timeConsoleView.editor as EditorImpl).scrollPane.verticalScrollBar.isVisible = false
        (timeConsoleView.editor as EditorImpl).scrollPane.verticalScrollBar.isEnabled = false
        (timeConsoleView.editor as EditorImpl).scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER;
        timeConsoleView.isVisible = false

        currentConsoleView = consoleView
        consoleView.setUpdateFoldingsEnabled(true)
        consoleView.editor.scrollingModel.addVisibleAreaListener {
            timeConsoleView.editor.scrollingModel.scrollVertically(it.newRectangle.y)
        }

        currentConsoleView.editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (event.oldLength > 0 && event.newLength == 0) {
                    currentTimeConsoleView.editor.document.deleteString(0, currentTimeConsoleView.editor.document.textLength)
                    logSize = 0
                    if (!filteringInProgress) {
                        logData.clear()
                    }
                }
            }
        })
        currentTimeConsoleView.editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (event.oldLength > 0 && event.newLength == 0) {
                    currentConsoleView.editor.document.deleteString(0, currentConsoleView.editor.document.textLength)
                }
            }
        })

        val actionGroup = DefaultActionGroup().apply {

            addAll(consoleView.createConsoleActions()
                    .filter {
                        !(it is PreviousOccurenceToolbarAction ||
                                it is NextOccurenceToolbarAction/* || it is ConsoleViewImpl.ClearAllAction*/)
                    }.toList())
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
        consoleView.add(timeConsoleView, BorderLayout.WEST)
        setContent(consoleView)
        setToolbar(toolbar)
    }
}
