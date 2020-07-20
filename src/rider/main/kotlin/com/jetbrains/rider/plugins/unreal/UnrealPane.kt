package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ide.actions.NextOccurenceToolbarAction
import com.intellij.ide.actions.PreviousOccurenceToolbarAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ComboBoxFieldPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.HorizontalLayout
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.ui.components.ComponentFactories
import com.jetbrains.rider.model.*
import com.jetbrains.rider.plugins.unreal.actions.*

import javax.swing.*
import java.awt.*

class UnrealPane(val model: Any, lifetime: Lifetime, val project: Project) : SimpleToolWindowPanel(false) {
    private val consoleView: ConsoleViewImpl = ComponentFactories.getConsoleView(project, lifetime)

    companion object {
        lateinit var currentConsoleView : ConsoleViewImpl
        val verbosityFilterActionGroup: FilterComboAction = FilterComboAction("Verbosity")
        val categoryFilterActionGroup: FilterComboAction = FilterComboAction("Categories")
        val timestampCheckBox: JBCheckBox = JBCheckBox("Show timestamps", false)
    }

    init {
        currentConsoleView = consoleView
        currentConsoleView.setUpdateFoldingsEnabled(true)

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
        setContent(consoleView)
        setToolbar(toolbar)
    }
}
