package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution

class EnableAutoUpdatePlugin : NotificationAction(UnrealLinkBundle.message("action.UnrealLink.EnableAutoUpdatePlugin.text")) {
    override fun actionPerformed(e: AnActionEvent, notifcation:Notification) {
        notifcation.expire()
        val project = e.project ?: return
        project.solution.rdRiderModel.enableAutoupdatePlugin.fire(Unit)
    }
}