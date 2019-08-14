package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.actions.NextOccurenceToolbarAction
import com.intellij.ide.actions.PreviousOccurenceToolbarAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.wm.ToolWindow

//import com.jetbrains.rider.plugins.unity.editorPlugin.model.*

class UnrealToolWindowContext(private val toolWindow: ToolWindow,
                              private val consoleView: ConsoleViewImpl) {
    fun print(event: String) = consoleView.print(event, ConsoleViewContentType.NORMAL_OUTPUT)
}