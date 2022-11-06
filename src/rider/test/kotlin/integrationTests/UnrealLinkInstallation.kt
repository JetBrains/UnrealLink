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
import com.jetbrains.rider.test.scriptingApi.buildWithChecks
import com.jetbrains.rider.test.scriptingApi.setConfigurationAndPlatform
import com.jetbrains.rider.test.scriptingApi.setReSharperBoolSetting
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.BeforeMethod
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
    // TODO think about no-copypaste decision
    init {
        projectDirectoryName = "EmptyUProject"
    }
    private val runProgramTimeout: Duration = Duration.ofMinutes(2)

    @BeforeMethod(alwaysRun = true)
    override fun prepareAndOpenSolution(parameters: Array<Any>) {
        val openSolutionWithParam = parameters[1] as EngineInfo.UnrealOpenType
        val engineParam = parameters[3] as UnrealEngine

        setReSharperBoolSetting("CppUnrealEngine/IndexEngine", false)
        configureAndOpenUnrealProject(openSolutionWithParam, engineParam)
    }

    @Test(dataProvider = "AllEngines_AllPModels")
    @RiderTestTimeout(30L, TimeUnit.MINUTES)
    fun installAndRun(
        @Suppress("UNUSED_PARAMETER") caseName: String,
        openWith: EngineInfo.UnrealOpenType,
        location: PluginInstallLocation,
        engine: UnrealEngine
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
        buildWithChecks(project, BuildSolutionAction(), "Build solution",
            useIncrementalBuild = false, timeout = buildTimeout)
//        checkThatBuildArtifactsExist(project)  // TODO create checker for unreal projects

        withRunProgram {
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
        unrealInfo.testingEngines.forEach { engine ->
            arrayOf(PluginInstallLocation.Game, PluginInstallLocation.Engine).forEach { location ->
                arrayOf(EngineInfo.UnrealOpenType.Sln, EngineInfo.UnrealOpenType.Uproject).forEach { type ->
                    // Install RL in UE5 in Engine breaks project build. See https://jetbrains.slack.com/archives/CH506NL5P/p1622199704007800 TODO?
                    if ((engine.id.startsWith("5.")) && engine.isInstalledBuild && location == PluginInstallLocation.Engine) return@forEach
                    result.add(arrayOf(uniqueDataString("$type$location", engine), type, location, engine))
                }
            }
        }
        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }
}
