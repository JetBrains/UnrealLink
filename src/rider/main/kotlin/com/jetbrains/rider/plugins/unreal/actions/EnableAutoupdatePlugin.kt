package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution

open class EnableAutoupdatePlugin : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        e.presentation.isEnabled = false
        project.solution.rdRiderModel.enableAutoupdatePlugin.fire(Unit)
    }
}