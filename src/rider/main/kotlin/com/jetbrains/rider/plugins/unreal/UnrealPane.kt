package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ide.actions.NextOccurenceToolbarAction
import com.intellij.ide.actions.PreviousOccurenceToolbarAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ComboBoxFieldPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.ui.components.ComponentFactories

import javax.swing.*
import java.awt.*

class UnrealPane(val model: Any, lifetime: Lifetime, val project: Project) : SimpleToolWindowPanel(false) {
    private val consoleView: ConsoleViewImpl = ComponentFactories.getConsoleView(project, lifetime)

    companion object {
        lateinit var currentConsoleView : ConsoleViewImpl
        val verbosityCombobox: ComboBoxFieldPanel = ComboBoxFieldPanel()
        val categoryCombobox: ComboBoxFieldPanel = ComboBoxFieldPanel()
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

        consoleView.scrollTo(0)

        val topPanel = JPanel(HorizontalLayout(0))

        verbosityCombobox.createComponent()
        val comboBox = verbosityCombobox.getComboBox()
        comboBox.addItem("All")
        comboBox.addItem("Verbose")
        comboBox.addItem("Log")
        comboBox.addItem("Display")
        comboBox.addItem("Warning")
        comboBox.addItem("Error")
        comboBox.addItem("Fatal")
        verbosityCombobox.setText("All")
        topPanel.add(JLabel("Verbosity"))
        topPanel.add(verbosityCombobox)

        categoryCombobox.createComponent()
        categoryCombobox.getComboBox().addItem("All")
        topPanel.add(JLabel("Category"))
        topPanel.add(categoryCombobox)

        consoleView.add(topPanel, BorderLayout.NORTH)
        setContent(consoleView)
        setToolbar(toolbar)
    }
}
