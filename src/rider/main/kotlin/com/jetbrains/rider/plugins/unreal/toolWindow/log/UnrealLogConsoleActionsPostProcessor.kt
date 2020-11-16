package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.actions.ConsoleActionsPostProcessor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.NextOccurenceToolbarAction
import com.intellij.ide.actions.PreviousOccurenceToolbarAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class UnrealLogConsoleActionsPostProcessor : ConsoleActionsPostProcessor() {
    override fun postProcess(console: ConsoleView, actions: Array<AnAction>): Array<AnAction> {
        if (console !is ConsoleViewImpl) {
            return super.postProcess(console, actions)
        }

        val consoleParent = console.parent
        if (consoleParent !is UnrealLogPanel) {
            return super.postProcess(console, actions)
        }

        return processActions(consoleParent, actions)

    }

    private fun processActions(panel: UnrealLogPanel, actions: Array<AnAction>): Array<AnAction> {
        val filteredActions =  actions.filter {
                    it !is PreviousOccurenceToolbarAction &&
                            it !is NextOccurenceToolbarAction &&
                            it !is ClearConsoleAction
                }.toMutableList()
        filteredActions.add(ClearUnrealConsoleAction(panel))
        return filteredActions.toTypedArray()
    }

    override fun postProcessPopupActions(console: ConsoleView, actions: Array<AnAction>): Array<AnAction> {
        if (console !is ConsoleViewImpl) {
            return super.postProcess(console, actions)
        }

        val consoleParent = console.parent
        if (consoleParent !is UnrealLogPanel) {
            return super.postProcess(console, actions)
        }

        return processPopupActions(consoleParent, actions)
    }

    private fun processPopupActions(panel: UnrealLogPanel, actions: Array<AnAction>): Array<AnAction> {
        val filteredActions = actions.filter {
            it !is ClearConsoleAction
        }.toMutableList()
        filteredActions.add(ClearUnrealConsoleAction(panel))
        return filteredActions.toTypedArray()
    }

    private class ClearUnrealConsoleAction(private val panel: UnrealLogPanel) :
            DumbAwareAction(ExecutionBundle.messagePointer("clear.all.from.console.action.name"),
                    ExecutionBundle.messagePointer("clear.all.from.console.action.description"),
                    AllIcons.Actions.GC) {

        override fun update(e: AnActionEvent) {
            val console = panel.console
            val enabled: Boolean = console.contentSize > 0
            e.presentation.isEnabled = enabled
        }

        override fun actionPerformed(e: AnActionEvent) {
            panel.clear()
        }
    }
}