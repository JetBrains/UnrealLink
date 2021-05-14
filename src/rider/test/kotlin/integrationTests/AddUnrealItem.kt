package integrationTests

import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions.UnrealTestWithSolution
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.scriptingApi.buildSolution
import com.jetbrains.rider.test.scriptingApi.checkBuildResult
import com.jetbrains.rider.test.scriptingApi.setConfigurationAndPlatform
import org.testng.annotations.Test
import java.time.Duration


@TestEnvironment(platform = [PlatformType.WINDOWS], toolset = ToolsetVersion.TOOLSET_16_CPP)
class AddUnrealItem : UnrealTestWithSolution() {
    init {
        solutionName = "EmptyUProject"
        installRiderLink = true
        openSolutionParams.waitForCaches = true
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(90)
        openSolutionParams.preprocessSolutionFile = { generateSolutionFromUProject(it) }
    }

    @Test
    fun connection() {

    }
}
