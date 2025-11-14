package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.Subsystem
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.annotations.report.Feature
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.Mono
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.unreal.UnrealProjectModelApiFacade
import com.jetbrains.rider.test.scriptingApi.reopenSolution
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import org.testng.annotations.Test
import java.time.Duration

@Subsystem("UnrealLink")
@Feature("Notification")
@TestSettings(buildTool = BuildTool.UNREAL, mono = Mono.UNIX_ONLY, sdkVersion = SdkVersion.DOT_NET_8, additionalSdkVersions = [SdkVersion.DOT_NET_6])
class RiderLinkNotification : UnrealLinkBase() {
  @Solution(RiderTestSolution.Unreal.EmptyUProject)
  @Test(dataProvider = "AllEngines_AllPModels")
  fun installNotification(
    @Suppress("UNUSED_PARAMETER") caseName: String,
    openWith: UnrealProjectModelApiFacade.PMType,
    engine: UnrealEngine
  ) {
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