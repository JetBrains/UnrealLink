package integrationTests.projectModel

import com.intellij.openapi.project.Project
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rider.projectView.SolutionConfigurationManager
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.framework.frameworkLogger
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.io.PrintStream
import java.time.Duration


@TestEnvironment(
    platform = [PlatformType.WINDOWS],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class UnrealConfigurations : UnrealTestProject() {
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
        unrealInfo.testingEngines.forEach { engine ->
            arrayOf(EngineInfo.UnrealOpenType.Uproject, EngineInfo.UnrealOpenType.Sln).forEach { type ->
                result.add(arrayOf(uniqueDataString("$type", engine), type, engine))
            }
        }
        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }

    @Test(dataProvider = "enginesAndOthers")
    fun solutionConfigurationsLoaded(
        @Suppress("UNUSED_PARAMETER") caseName: String,
        openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine
    ) {
        unrealInTestSetup(openWith, engine)
        project = openProject(openWith)
        executeWithGold(testGoldFile) { printStream ->
            doTestDumpSolutionConfigurations(project, printStream)
        }
    }

    fun doTestDumpSolutionConfigurations(project: Project, printStream: PrintStream) {
        val slnConfManager = SolutionConfigurationManager.getInstance(project)
        val slnConfAndPlatforms = slnConfManager.solutionConfigurationsAndPlatforms

        printStream.println("Count: ${slnConfAndPlatforms.size}")
        for (cfg in slnConfAndPlatforms)
            printStream.println(cfg)
    }

    // Mandatory function before opening an unreal project
    private fun unrealInTestSetup(openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInfo.currentEngine = engine

        println("Test starting with $engine, opening by $openWith.")

        replaceUnrealEngineVersionInUproject(uprojectFile, unrealInfo.currentEngine!!)

        if (openWith == EngineInfo.UnrealOpenType.Sln) {
            generateSolutionFromUProject(uprojectFile)
            openSolutionParams.minimalCountProjectsMustBeLoaded = null
        } else {
            openSolutionParams.minimalCountProjectsMustBeLoaded =
                1400 // TODO: replace the magic number with something normal
        }
    }
}