package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ide.actions.NextOccurenceToolbarAction
import com.intellij.ide.actions.PreviousOccurenceToolbarAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.plugins.unreal.filters.UnrealHeavyLogFilter
import com.jetbrains.rider.ui.components.ComponentFactories

class UnrealPane(val model: Any, lifetime: Lifetime, val project: Project) : SimpleToolWindowPanel(false) {
    private val consoleView: ConsoleViewImpl = ComponentFactories.getConsoleView(project, lifetime)

    companion object {
        lateinit var currentConsoleView : ConsoleViewImpl
    }

    init {
        currentConsoleView = consoleView
        currentConsoleView.addMessageFilter(UnrealHeavyLogFilter(project))
        currentConsoleView.allowHeavyFilters()
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

        setContent(consoleView)
        setToolbar(toolbar)
    }
}
