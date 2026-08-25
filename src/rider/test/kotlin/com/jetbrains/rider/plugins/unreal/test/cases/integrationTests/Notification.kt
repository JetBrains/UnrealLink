package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.Subsystem
import com.jetbrains.rider.test.annotations.report.ChecklistItems
import com.jetbrains.rider.test.annotations.report.Feature
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import com.jetbrains.rider.test.junit5.unreal.UnrealCombinations
import com.jetbrains.rider.test.reporting.SubsystemConstants
import com.jetbrains.rider.test.scriptingApi.reopenSolution
import com.jetbrains.rider.test.shared.constants.TeamCityTags
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import com.jetbrains.rider.test.unreal.UnrealEnvironment
import java.time.Duration
import org.junit.jupiter.api.Tag

@Tag(TeamCityTags.GameDev.Unreal.Link.General)
@Tag(TeamCityTags.GameDev.Unreal.Link.Smoke)
@Subsystem(SubsystemConstants.UNREAL_LINK)
@Feature("Notification")
class RiderLinkNotification : UnrealLinkBase() {
  @Solution(RiderTestSolution.Unreal.EmptyUProject)
  @UnrealCombinations
  @ChecklistItems(["UnrealLink/Installation Notification"])
  fun installNotification(e: UnrealEnvironment) {
    val notification = NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(Notification::class.java, project).single { it.groupId == "OutOfSyncConnection" }
    notification.type.shouldBe(NotificationType.WARNING)
    notification.title.shouldBe(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.title.notInstalled"))
    notification.actions.size.shouldBe(2)
    notification.actions.any { it.templateText.equals(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInEngine.text")) }.shouldNotBeNull()
    notification.actions.any { it.templateText.equals(UnrealLinkBundle.message("notificationAction.UnrealEditorOutOfSync.installPluginInGame.text")) }.shouldNotBeNull()
 
    installRiderLink(PluginInstallLocation.Game)

    reopenSolution(project, Duration.ofMinutes(3))

    NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java,
                                                                          project).none { it.groupId == "OutOfSyncConnection" }.shouldBeTrue()
  }
}
