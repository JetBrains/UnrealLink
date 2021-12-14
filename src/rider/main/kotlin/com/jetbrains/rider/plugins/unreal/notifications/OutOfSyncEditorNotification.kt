package com.jetbrains.rider.plugins.unreal.notifications

import com.intellij.notification.*
import com.intellij.openapi.project.Project
import com.jetbrains.rd.platform.util.idea.ProtocolSubscribedProjectComponent
import com.jetbrains.rd.util.reactive.adviseNotNull
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.*
import com.jetbrains.rider.projectView.solution

class OutOfSyncEditorNotification(project: Project) : ProtocolSubscribedProjectComponent(project) {
    // TO-DO: move all public strings to resource bundle
    companion object {
        private val notificationGroupId =
            NotificationGroup.createIdWithTitle(
                "Unreal Editor connection is out of sync",
                UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.groupId")
            )

    }

    init {
        project.solution.rdRiderModel.onEditorPluginOutOfSync.adviseNotNull(projectComponentLifetime) {
            if (it.status == PluginInstallStatus.UpToDate) return@adviseNotNull

            val message = if (it.status == PluginInstallStatus.NoPlugin)
                UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.description.notInstalled")
            else
                UnrealLinkBundle.message(
                    "notificationAction.UnrealEditorOutOfSync.description.wrongVersion",
                    it.installedVersion,
                    it.requiredVersion
                )

            val title = if (it.status == PluginInstallStatus.NoPlugin)
                UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.title.notInstalled")
            else
                UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.title.wrongVersion")

            val notification = Notification(notificationGroupId, title, message, NotificationType.WARNING)

            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (it.status) {
                PluginInstallStatus.NoPlugin -> {
                    notification.apply {
                        if (it.isEngineAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInEngine.text")) {
                            expire()
                            project.solution.rdRiderModel.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.No)
                            )
                        })
                        if (it.isGameAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInGame.text")) {
                            expire()
                            project.solution.rdRiderModel.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.No)
                            )
                        })
                    }
                }
                PluginInstallStatus.InEngine -> {
                    notification.apply {
                        if (it.isEngineAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInEngine.text.update")) {
                            expire()
                            project.solution.rdRiderModel.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.No)
                            )
                        })
                        if (it.isGameAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInGame.text.move")) {
                            expire()
                            project.solution.rdRiderModel.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.No)
                            )
                        })
                    }
                }
                PluginInstallStatus.InGame -> {
                    notification.apply {
                        if (it.isEngineAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInEngine.text.move")) {
                            expire()
                            project.solution.rdRiderModel.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.No)
                            )
                        })
                        if (it.isGameAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInGame.text.update")) {
                            expire()
                            project.solution.rdRiderModel.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.No)
                            )
                        })
                    }
                }
            }

            Notifications.Bus.notify(notification, project)
        }
    }
}
