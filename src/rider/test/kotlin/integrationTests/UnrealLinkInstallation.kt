package integrationTests

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.RiderTestTimeout
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.getLoadedProjects
import com.jetbrains.rider.test.scriptingApi.buildWithChecks
import com.jetbrains.rider.test.scriptingApi.setConfigurationAndPlatform
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.time.Duration
import java.util.concurrent.TimeUnit

@Epic("UnrealLink")
@Feature("Installation")
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
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(300)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(900)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)
    }
    private val runProgramTimeout: Duration = Duration.ofMinutes(2)

    @Test(dataProvider = "enginesAndOthers")
    @RiderTestTimeout(30L, TimeUnit.MINUTES)
    fun installAndRun(
        @Suppress("UNUSED_PARAMETER") caseName: String,
        openWith: EngineInfo.UnrealOpenType,
        location: PluginInstallLocation,
        engine: UnrealEngine
    ) {
        unrealInfo.placeToInstallRiderLink = location
        unrealInfo.needInstallRiderLink = true

        println("Test starting with $engine, RiderLink will install in $location, opening by $openWith.")

        unrealInTestSetup(openWith, engine, location != PluginInstallLocation.Engine)

        getLoadedProjects(project)
        waitAndPump(Duration.ofSeconds(15),
            { project.solution.rdRiderModel.isUnrealEngineSolution.value }, { "This is not unreal solution" })

        if (unrealInfo.needInstallRiderLink) {
            installRiderLink(unrealInfo.placeToInstallRiderLink)
        }

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")
        buildWithChecks(project, BuildSolutionAction(), "Build solution",
            useIncrementalBuild = false, timeout = buildTimeout)
//        checkThatBuildArtifactsExist(project)  // TODO create checker for unreal projects

        withRunProgram {
            waitAndPump(runProgramTimeout,
                { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })
        }
    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        val guidRegex = "^[{]?[\\da-fA-F]{8}-([\\da-fA-F]{4}-){3}[\\da-fA-F]{12}[}]?$".toRegex()

        // Little hack for generate unique name in com.jetbrains.rider.test.TestCaseRunner#extractTestName
        //  based on file template type, UnrealOpenType, engine version and what engine uses - EGS/Source.
        // Unique name need for gold file/dir name.
        val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, engine: UnrealEngine ->
            // If we use engine from source, it's ID is GUID, so we replace it by 'normal' id plus ".fromSouce" string
            // else just replace dots in engine version, 'cause of part after last dot will be parsed as file type.
            if (engine.id.matches(guidRegex)) "$baseString${engine.version.major}_${engine.version.minor}fromSource"
            else "$baseString${engine.id.replace('.', '_')}"
        }
        unrealInfo.testingEngines.filter { !it.isInstalledBuild }.forEach { engine ->
            arrayOf(PluginInstallLocation.Game, PluginInstallLocation.Engine).forEach { location ->
                arrayOf(EngineInfo.UnrealOpenType.Sln, EngineInfo.UnrealOpenType.Uproject).forEach { type ->
                    // Install RL in UE5 in Engine breaks project build. See https://jetbrains.slack.com/archives/CH506NL5P/p1622199704007800 TODO?
                    if ((engine.id.startsWith("5.")) && engine.isInstalledBuild && location == PluginInstallLocation.Engine) return@forEach
                    result.add(arrayOf(uniqueDataString("$type$location", engine), type, location, engine))
                }
            }
        }
        return result.iterator()
    }
}
