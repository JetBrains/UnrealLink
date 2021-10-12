package integrationTests

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions.UnrealTestInfo
import testFrameworkExtentions.UnrealTestProject
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseUntil
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.getLoadedProjects
import com.jetbrains.rider.test.scriptingApi.buildSolution
import com.jetbrains.rider.test.scriptingApi.checkBuildResult
import com.jetbrains.rider.test.scriptingApi.setConfigurationAndPlatform
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.time.Duration

@TestEnvironment(platform = [PlatformType.WINDOWS], toolset = ToolsetVersion.TOOLSET_16_CPP)
class Connection : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
        openSolutionParams.waitForCaches = true
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(150)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(150)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)

    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        unrealInfo.testingEngines.forEach { engine ->
            arrayOf(PluginInstallLocation.Game).forEach { location ->
                arrayOf(UnrealTestInfo.UnrealOpenType.Sln, UnrealTestInfo.UnrealOpenType.Uproject).forEach { type ->
                    result.add(arrayOf(type, location, engine))
                }
            }
        }
        return result.iterator()
    }

    @Test (dataProvider = "enginesAndOthers")
    fun connection(openWith: UnrealTestInfo.UnrealOpenType, location: PluginInstallLocation, engine: UnrealEngine) {
//    fun connection() {
//
//        val location = PluginInstallLocation.Game
//        val openWith = UnrealTestInfo.UnrealOpenType.Sln
//        val engine = unrealInfo.testingEngines.find { it.id == "5.0EA" && it.isInstalledBuild }!!

        unrealInfo.currentEngine = engine
        unrealInfo.placeToInstallRiderLink = location

        println("Test starting with $engine, RiderLink will install in $location, opening by $openWith.")

        replaceUnrealEngineVersionInUproject(uprojectFile, unrealInfo.currentEngine!!)

        if (openWith == UnrealTestInfo.UnrealOpenType.Sln) {
            generateSolutionFromUProject(uprojectFile)
            openSolutionParams.minimalCountProjectsMustBeLoaded = null
        }
        else {
            openSolutionParams.minimalCountProjectsMustBeLoaded = 1400
        }

        project = openProject(openWith)
        getLoadedProjects(project)

        waitAndPump(Duration.ofSeconds(15),
            { project.solution.rdRiderModel.isUnrealEngineSolution.value }, { "This is not unreal solution" })

//        var isUnrealSolution = false
//        project.solution.rdRiderModel.isUnrealEngineSolution.adviseUntil(Lifetime.Eternal)
//            { isUnrealSolution = it
//                it }
//        waitAndPump(Duration.ofSeconds(15),
//            { isUnrealSolution }, { "Fuck!" })

        if (unrealInfo.needInstallRiderLink) {
            installRiderLink(unrealInfo.placeToInstallRiderLink)
        }

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")

        val result = buildSolution(project, timeout = Duration.ofSeconds(120), useIncrementalBuild = false)
        checkBuildResult(result.buildResult, result.errorMessages)
//        checkThatBuildArtifactsExist(project)  // TODO create checker for unreal projects

        withRunProgram {
            waitAndPump(Duration.ofSeconds(60),
                { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })
        }
    }
}
