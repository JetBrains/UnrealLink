package integrationTests

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import testFrameworkExtentions.UnrealTestProject
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.getLoadedProjects
import com.jetbrains.rider.test.scriptingApi.buildWithChecks
import com.jetbrains.rider.test.scriptingApi.setConfigurationAndPlatform
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import java.time.Duration

@TestEnvironment(
    platform = [PlatformType.WINDOWS],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class UnrealLinkInstallation : UnrealTestProject() {
    // TODO think about no-copypaste decision
    init {
        projectDirectoryName = "EmptyUProject"
        openSolutionParams.waitForCaches = true
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(150)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(180)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)
    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        unrealInfo.testingEngines.forEach { engine ->
            arrayOf(PluginInstallLocation.Game, PluginInstallLocation.Engine).forEach { location ->
                arrayOf(EngineInfo.UnrealOpenType.Sln, EngineInfo.UnrealOpenType.Uproject).forEach { type ->
                    // Install RL in UE5 in Engine breaks project build. See https://jetbrains.slack.com/archives/CH506NL5P/p1622199704007800 TODO?
                    if ((engine.id == "5.0EA" || engine.id == "5.0") && engine.isInstalledBuild && location == PluginInstallLocation.Engine) return@forEach
                    // TODO delete before commit. It's local hack
                    if (engine.path.endsWith("REPO\\UE5")) return@forEach
                    result.add(arrayOf(type, location, engine))
                }
            }
        }
        return result.iterator()
    }

    @Test(dataProvider = "enginesAndOthers")
    fun installationAndRun(openWith: EngineInfo.UnrealOpenType, location: PluginInstallLocation, engine: UnrealEngine) {
        unrealInfo.currentEngine = engine
        unrealInfo.placeToInstallRiderLink = location

        println("Test starting with $engine, RiderLink will install in $location, opening by $openWith.")

        replaceUnrealEngineVersionInUproject(uprojectFile, unrealInfo.currentEngine!!)

        if (openWith == EngineInfo.UnrealOpenType.Sln) {
            generateSolutionFromUProject(uprojectFile)
            openSolutionParams.minimalCountProjectsMustBeLoaded = null
        } else {
            openSolutionParams.minimalCountProjectsMustBeLoaded = 1400
        }

        project = openProject(openWith)

        getLoadedProjects(project)
        waitAndPump(Duration.ofSeconds(15),
            { project.solution.rdRiderModel.isUnrealEngineSolution.value }, { "This is not unreal solution" })

        if (unrealInfo.needInstallRiderLink) {
            installRiderLink(unrealInfo.placeToInstallRiderLink)
        }

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")
        buildWithChecks(project, BuildSolutionAction(), "Build solution", useIncrementalBuild = false)
//        checkThatBuildArtifactsExist(project)  // TODO create checker for unreal projects

        withRunProgram {
            waitAndPump(Duration.ofSeconds(60),
                { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })
        }
    }

    // Special test template for manual launch with specific parameters.
    // Just do "enable = true" and set openWith, engine and template variables.
    @Test(enabled = false)
    fun installationAndRunSingle() {
        val location = PluginInstallLocation.Engine
        val openWith = EngineInfo.UnrealOpenType.Uproject
        val engine = unrealInfo.testingEngines.find { it.id == "4.27" && it.isInstalledBuild }!!
        installationAndRun(openWith, location, engine)
    }
}
