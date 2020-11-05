package com.jetbrains.rider.plugins.unreal.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.jetbrains.rd.platform.util.idea.ProtocolSubscribedProjectComponent
import com.jetbrains.rd.util.reactive.adviseNotNull
import com.jetbrains.rider.plugins.unreal.actions.InstallEditorPluginToEngineAction
import com.jetbrains.rider.plugins.unreal.actions.InstallEditorPluginToGameAction
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallStatus
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution

class OutOfSyncEditorNotification(project: Project): ProtocolSubscribedProjectComponent(project) {
    // TODO: move all public strings to resource bundle
    companion object {
        private val notificationGroupId =
                NotificationGroup.createIdWithTitle("Unreal Editor connection is out of sync",
                        "Unreal Editor connection is out of sync")
    }

    init {
        project.solution.rdRiderModel.onEditorPluginOutOfSync.adviseNotNull(projectComponentLifetime) {
            if(it.status == PluginInstallStatus.UpToDate) return@adviseNotNull

            val message = if(it.status == PluginInstallStatus.NoPlugin) {
                "The RiderLink Unreal Editor plugin is not installed." +
                " Advanced Unreal integration features are unavailable until the plugin is updated."
            }
            else {
                "<html>" +
                "The RiderLink Unreal Editor plugin is not in sync with UnrealLink Rider plugin." +
                " Different versions of plugins are not guaranteed to work together.<br>" +
                "Installed version:\t${it.installedVersion}<br>"+
                "Required version:\t${it.requiredVersion}" +
                "</html>"
            }

            val title = if(it.status == PluginInstallStatus.NoPlugin)
                "RiderLink plugin is required"
            else
                "RiderLink plugin synchronization is required"

            val notification = Notification(notificationGroupId, title, message, NotificationType.WARNING)

            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (it.status) {
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
