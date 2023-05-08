package integrationTests

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.scriptingApi.reopenSolution
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.time.Duration

@Epic("UnrealLink")
@Feature("Notification")
@TestEnvironment(
    buildTool = BuildTool.CPP,
    sdkVersion = SdkVersion.AUTODETECT
)
class RiderLinkNotification : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }
    @Test(dataProvider = "AllEngines_AllPModels")
    fun installNotification(
        @Suppress("UNUSED_PARAMETER") caseName: String,
        openWith: EngineInfo.UnrealOpenType,
        engine: UnrealEngine
    ) {
        val notification = NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
                .single { it.groupId == "OutOfSyncConnection" }
        notification.type.shouldBe(NotificationType.WARNING)
        notification.title.shouldBe("RiderLink plugin is required")
        notification.actions.size.shouldBe(2)
        notification.actions.any { it.templateText.equals("Install plugin in Engine") }.shouldNotBeNull()
        notification.actions.any { it.templateText.equals("Install plugin in Game") }.shouldNotBeNull()

        unrealInfo.needInstallRiderLink = true
        installRiderLink(unrealInfo.placeToInstallRiderLink)

        
        reopenSolution(project, Duration.ofMinutes(3))

        NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project).none { it.groupId == "OutOfSyncConnection" }.shouldBeTrue()
    }
}