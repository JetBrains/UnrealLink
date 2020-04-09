package com.jetbrains.rider.plugins.unreal.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.reactive.adviseNotNull
import com.jetbrains.rdclient.util.idea.ProtocolSubscribedProjectComponent
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.intellij.openapi.actionSystem.ActionManager

class OutOfSyncEditorNotification(project: Project): ProtocolSubscribedProjectComponent(project) {
    companion object {
        private val notificationGroupId = NotificationGroup.balloonGroup("Unreal Editor connection is out of sync")
    }

    init {
        project.solution.rdRiderModel.onEditorModelOutOfSync.adviseNotNull(componentLifetime) {
            val message = "The RiderLink Unreal Editor plugin is out of date and automatic plugin updates are disabled. Advanced Unreal integration features are unavailable until the plugin is updated."

            val notification = Notification(notificationGroupId.displayId, "Unreal Editor plugin update required", message, NotificationType.WARNING)
            val action = ActionManager.getInstance().getAction("UnrealLink.InstallEditorPluginAction")
            notification.addAction(action)
            Notifications.Bus.notify(notification, project)
        }
    }
}