package com.jetbrains.rider.plugins.unreal.notifications

import com.intellij.notification.*
import com.intellij.openapi.client.ClientProjectSession
import com.jetbrains.rd.protocol.SolutionExtListener
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseNotNull
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.*

class OutOfSyncPluginProtocolListener : SolutionExtListener<RdRiderModel> {
    companion object {
        private const val OUT_OF_SYNC_NOTIFICATION_GROUP_ID = "OutOfSyncConnection"
    }

    override fun extensionCreated(lifetime: Lifetime, session: ClientProjectSession, model: RdRiderModel) {
        model.onEditorPluginOutOfSync.adviseNotNull(lifetime) {
            if (it.status == PluginInstallStatus.UpToDate) return@adviseNotNull

            val message = if (it.status == PluginInstallStatus.NoPlugin)
                UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.description.notInstalled")
            else
                UnrealLinkBundle.message(
                    "notificationAction.UnrealEditorOutOfSync.description.wrongVersion"
                )

            val title = if (it.status == PluginInstallStatus.NoPlugin)
                UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.title.notInstalled")
            else
                UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.title.wrongVersion")

            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(OUT_OF_SYNC_NOTIFICATION_GROUP_ID)
                .createNotification(title, message, NotificationType.WARNING)

            when (it.status) {
                PluginInstallStatus.NoPlugin -> {
                    notification.apply {
                        addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInEngine.text")) {
                            expire()
                            model.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.No)
                            )
                        })
                        if (it.isGameAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInGame.text")) {
                            expire()
                            model.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.No)
                            )
                        })
                    }
                }
                PluginInstallStatus.InEngine -> {
                    notification.apply {
                        addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInEngine.text.update")) {
                            expire()
                            model.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.No)
                            )
                        })
                        if (it.isGameAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInGame.text.move")) {
                            expire()
                            model.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.No)
                            )
                        })
                    }
                }
                PluginInstallStatus.InGame -> {
                    notification.apply {
                        addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInEngine.text.move")) {
                            expire()
                            model.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.No)
                            )
                        })
                        if (it.isGameAvailable) addAction(NotificationAction.createSimple(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInGame.text.update")) {
                            expire()
                            model.installEditorPlugin.fire(
                                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.No)
                            )
                        })
                    }
                }
                else -> return@adviseNotNull
            }

            Notifications.Bus.notify(notification, session.project)
        }
    }
}
