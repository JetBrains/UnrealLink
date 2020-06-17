package com.jetbrains.rider.plugins.unreal.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.reactive.adviseNotNull
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.intellij.openapi.actionSystem.ActionManager
import com.jetbrains.rd.platform.util.idea.ProtocolSubscribedProjectComponent
import com.jetbrains.rider.model.PluginInstallStatus
import com.jetbrains.rider.plugins.unreal.actions.InstallEditorPluginToEngineAction
import com.jetbrains.rider.plugins.unreal.actions.InstallEditorPluginToGameAction

class OutOfSyncEditorNotification(project: Project): ProtocolSubscribedProjectComponent(project) {
    companion object {
        private val notificationGroupId = NotificationGroup.balloonGroup("Unreal Editor connection is out of sync")
    }

    init {
        project.solution.rdRiderModel.onEditorModelOutOfSync.adviseNotNull(projectComponentLifetime) {
            if(it == PluginInstallStatus.UpToDate) return@adviseNotNull

            var message = if(it == PluginInstallStatus.NoPlugin)
                "The RiderLink Unreal Editor plugin is not installed and automatic plugin updates are disabled."
            else
                "The RiderLink Unreal Editor plugin is out of date and automatic plugin updates are disabled."
            message +=  " Advanced Unreal integration features are unavailable until the plugin is updated."

            val title = if(it == PluginInstallStatus.NoPlugin)
                "RiderLink plugin is required"
            else
                "RiderLink plugin update is required"

            val notification = Notification(notificationGroupId.displayId, title, message, NotificationType.WARNING)

            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (it) {
                PluginInstallStatus.NoPlugin -> {
                    notification.addAction(ActionManager.getInstance().getAction("UnrealLink.InstallEditorPluginToEngineAction"))
                    notification.addAction(ActionManager.getInstance().getAction("UnrealLink.InstallEditorPluginToGameAction"))
                }
                PluginInstallStatus.InEngine -> {
                    notification.addAction(object : InstallEditorPluginToEngineAction("Update Plugin in Engine"){})
                    notification.addAction(object : InstallEditorPluginToGameAction("Move Plugin to Game"){})
                }
                PluginInstallStatus.InGame -> {
                    notification.addAction(object : InstallEditorPluginToEngineAction("Move Plugin to Engine"){})
                    notification.addAction(object : InstallEditorPluginToGameAction("Update Plugin in Game"){})
                }
            }

            Notifications.Bus.notify(notification, project)
        }
    }
}