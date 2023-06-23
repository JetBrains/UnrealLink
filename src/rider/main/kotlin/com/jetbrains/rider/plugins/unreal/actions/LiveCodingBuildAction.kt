package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution

class LiveCodingBuildAction : DumbAwareAction(AllIcons.Actions.Refresh) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getUnrealHost()
        if (host == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        if (!host.isUnrealEngineSolution || !host.isLiveCodingAvailable) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isVisible = true
        e.presentation.isEnabled = !host.isLiveCodingCompiling
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.solution.rdRiderModel.triggerLiveCodingBuild.fire()
    }
}