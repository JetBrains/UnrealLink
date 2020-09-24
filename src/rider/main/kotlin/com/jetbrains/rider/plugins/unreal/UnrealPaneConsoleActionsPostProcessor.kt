package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.actions.ConsoleActionsPostProcessor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.actions.NextOccurenceToolbarAction
import com.intellij.ide.actions.PreviousOccurenceToolbarAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class UnrealPaneConsoleActionsPostProcessor : ConsoleActionsPostProcessor() {
    override fun postProcess(console: ConsoleView, actions: Array<AnAction>): Array<AnAction> {
        if (console !is ConsoleViewImpl) {
            return super.postProcess(console, actions)
        }

        val consoleParent = console.parent
        if (consoleParent !is UnrealPane) {
            return super.postProcess(console, actions)
        }

        return processActions(consoleParent, actions)

    }

    private fun processActions(consolePanel: UnrealPane, actions: Array<AnAction>): Array<AnAction> {
        val filteredActions =  actions.filter {
                    it !is PreviousOccurenceToolbarAction &&
                            it !is NextOccurenceToolbarAction &&
                            it !is ClearConsoleAction
                }.toMutableList()
        filteredActions.add(ClearUnrealConsoleAction(consolePanel))
        return filteredActions.toTypedArray()
    }

    override fun postProcessPopupActions(console: ConsoleView, actions: Array<AnAction>): Array<AnAction> {
        if (console !is ConsoleViewImpl) {
            return super.postProcess(console, actions)
        }

        val consoleParent = console.parent
        if (consoleParent !is UnrealPane) {
            return super.postProcess(console, actions)
        }

        return processPopupActions(consoleParent, actions)
    }

    private fun processPopupActions(consolePanel: UnrealPane, actions: Array<AnAction>): Array<AnAction> {
        val filteredActions = actions.filter {
            it !is ClearConsoleAction
        }.toMutableList()
        filteredActions.add(ClearUnrealConsoleAction(consolePanel))
        return filteredActions.toTypedArray()
    }

    private class ClearUnrealConsoleAction(val consolePanel: UnrealPane) : ClearConsoleAction() {
        override fun actionPerformed(e: AnActionEvent) {
            UnrealPane.logData.clear()

            super.actionPerformed(e)
        }
    }
}