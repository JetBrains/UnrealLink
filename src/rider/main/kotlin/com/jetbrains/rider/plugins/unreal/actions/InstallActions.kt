package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rd.ide.model.PluginInstallLocation
import com.jetbrains.rd.ide.model.rdRiderModel
import com.jetbrains.rider.projectView.solution

open class InstallEditorPluginToEngineAction(text: String = "Install in Engine") : NotificationAction(text) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
        val project = e.project ?: return
        e.presentation.isEnabled = false
        project.solution.rdRiderModel.installEditorPlugin.fire(PluginInstallLocation.Engine)
    }
}

open class InstallEditorPluginToGameAction(text: String = "Install in Game") : NotificationAction(text) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
        val project = e.project ?: return
        e.presentation.isEnabled = false
        project.solution.rdRiderModel.installEditorPlugin.fire(PluginInstallLocation.Game)
    }
}