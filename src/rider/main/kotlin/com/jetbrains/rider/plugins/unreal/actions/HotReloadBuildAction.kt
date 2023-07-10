package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.application
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import icons.RiderIcons

class HotReloadBuildAction : DumbAwareAction(RiderIcons.Actions.CompileLive) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getUnrealHost()
        if (host == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        if (!host.isUnrealEngineSolution || !host.isHotReloadAvailable) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isVisible = true
        e.presentation.isEnabled = !host.isHotReloadCompiling
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        application.assertIsDispatchThread()
        application.saveAll()
        project.solution.rdRiderModel.triggerHotReload.fire()
    }
}