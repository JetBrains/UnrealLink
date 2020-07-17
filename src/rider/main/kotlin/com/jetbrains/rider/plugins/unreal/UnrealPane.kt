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
import com.jetbrains.rider.model.*
import com.jetbrains.rider.plugins.unreal.actions.*

import javax.swing.*
import java.awt.*

class UnrealPane(val model: Any, lifetime: Lifetime, val project: Project) : SimpleToolWindowPanel(false) {
    private val consoleView: ConsoleViewImpl = ComponentFactories.getConsoleView(project, lifetime)

    companion object {
        lateinit var currentConsoleView : ConsoleViewImpl
        val verbosityFilterActionGroup: FilterComboAction = FilterComboAction("", arrayListOf())
        val categoryFilterActionGroup: FilterComboAction = FilterComboAction("All", arrayListOf("All"))
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

        for (verbosity in VerbosityType.values()) {
            verbosityFilterActionGroup.addItem(verbosity.name)
            if (verbosity.equals(VerbosityType.VeryVerbose)) {
                verbosityFilterActionGroup.setText(verbosity.name)
                break
            }
        }
        val verbosityGroup = DefaultActionGroup().apply {
            add(verbosityFilterActionGroup)
        }
        val verbosityToolbar = ActionManager.getInstance().createActionToolbar("", verbosityGroup, true).component

        val categoryGroup = DefaultActionGroup().apply {
            add(categoryFilterActionGroup)
        }
        val categoryToolbar = ActionManager.getInstance().createActionToolbar("", categoryGroup, true).component

        consoleView.scrollTo(0)

        val topPanel = JPanel(HorizontalLayout(0))

        topPanel.add(JLabel("Verbosity"))
        topPanel.add(verbosityToolbar)

        topPanel.add(JLabel("Category"))
        topPanel.add(categoryToolbar)
        topPanel.border = BorderFactory.createEmptyBorder(0, 5, 0, 0)

        consoleView.add(topPanel, BorderLayout.NORTH)
        setContent(consoleView)
        setToolbar(toolbar)
    }
}
