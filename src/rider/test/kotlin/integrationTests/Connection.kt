package integrationTests

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
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


class Connection : UnrealTestWithSolution() {

    override val waitForCaches = true

    override val backendLoadedTimeout: Duration
        get() = Duration.ofSeconds(60)

    override fun getSolutionDirectoryName(): String = "EmptyUProject"

    @Test
    @TestEnvironment(platform = [PlatformType.WINDOWS], toolset = ToolsetVersion.TOOLSET_16_CPP)
    fun connection() {
        waitAndPump(Duration.ofSeconds(15), { project.solution.rdRiderModel.isUnrealEngineSolution.value }, { "This is not unreal solution" })

        // TODO move plugin installation to suite level
        var riderLinkInstalled = false
        project.solution.rdRiderModel.installPluginFinished.advise(Lifetime.Eternal) { riderLinkInstalled = true }
        project.solution.rdRiderModel.installEditorPlugin.fire(
            InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes))

        waitAndPump(Duration.ofSeconds(90), { riderLinkInstalled })

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")

        val result = buildSolution(project, timeout = Duration.ofSeconds(120))
        checkBuildResult(result.buildResult, result.errorMessages)
//        checkThatBuildArtifactsExist(project)  // TODO create checker for unreal projects

        withRunProgram {
            waitAndPump(Duration.ofSeconds(60),
                { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })
        }
    }
}
