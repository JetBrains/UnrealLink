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
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.framework.getLoadedProjects
import com.jetbrains.rider.test.scriptingApi.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.time.Duration
import java.util.concurrent.TimeUnit

@Epic("UnrealLink")
@Feature("Installation")
@TestEnvironment(
    platform = [PlatformType.WINDOWS_X64],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class UnrealLinkInstallation : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
        disableEnginePlugins = false
    }
    private val runProgramTimeout: Duration = Duration.ofMinutes(2)

    @Test(dataProvider = "AllEngines_AllPModels")
    @RiderTestTimeout(30L, TimeUnit.MINUTES)
    fun ul(
        @Suppress("UNUSED_PARAMETER") caseName: String,
        openWith: EngineInfo.UnrealOpenType,
        engine: UnrealEngine,
        location: PluginInstallLocation
    ) {
        unrealInfo.placeToInstallRiderLink = location
        unrealInfo.needInstallRiderLink = true
        println("RiderLink will install in $location")

        getLoadedProjects(project)
        waitAndPump(Duration.ofSeconds(15),
            { project.solution.rdRiderModel.isUnrealEngineSolution.value }, { "This is not unreal solution" })

        if (unrealInfo.needInstallRiderLink) {
            installRiderLink(unrealInfo.placeToInstallRiderLink)
        }

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")
        buildWithChecks(
            project, BuildSolutionAction(), "Build solution",
            useIncrementalBuild = false, timeout = buildTimeout
        )
//        checkThatBuildArtifactsExist(project)  // TODO create checker for unreal projects

        withRunProgram(configurationName = activeSolution) {
            waitAndPump(runProgramTimeout,
                { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })
        }
    }

    /**
     * [UnrealLinkInstallation] have additional parameter - location ([PluginInstallLocation]), so we need to override
     * data provider generating.
     */
    override fun generateUnrealDataProvider(unrealPmTypes: Array<EngineInfo.UnrealOpenType>,
                                             predicate: (UnrealEngine) -> Boolean): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        /**
         * [unrealInfo] initialized in [suiteSetup]. Right before data provider invocation
         */
        unrealInfo.testingEngines.filter(predicate).ifEmpty {
            throw Exception("Failed to filter engines in ${unrealInfo.testingEngines} by $predicate")
        }.forEach { engine ->
            arrayOf(PluginInstallLocation.Game, PluginInstallLocation.Engine).forEach { location ->
                arrayOf(EngineInfo.UnrealOpenType.Sln, EngineInfo.UnrealOpenType.Uproject).forEach { type ->
                    // Install RL in UE5 in Engine breaks project build. See https://jetbrains.slack.com/archives/CH506NL5P/p1622199704007800 TODO?
                    if ((engine.version.major == 5) && engine.isInstalledBuild && location == PluginInstallLocation.Engine) return@forEach
                    result.add(arrayOf(uniqueDataString("$type$location", engine), type, engine, location))
                }
            }
        }
        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }

    override val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, engine: UnrealEngine ->
        // If we use engine from source, it's ID is GUID, so we replace it by 'normal' id plus ".fromSouce" string
        // else just replace dots in engine version, 'cause of part after last dot will be parsed as file type.
        if (engine.id.matches(guidRegex)) "$baseString${engine.version.major}_${engine.version.minor}_Src"
        else "$baseString${engine.id.replace('.', '_')}"
    }
}
