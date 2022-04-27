package integrationTests.debug

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RiderRunConfigurationBase
import com.jetbrains.rider.test.base.BaseTestWithSolution
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.io.File
import java.time.Duration

class BreakpointBase : UnrealTestProject() {
    init {
        projectDirectoryName = "TestPuzzleProject"
        openSolutionParams.waitForCaches = true
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(150)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(150)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)
    }

    @Test
    fun testToggleBreakpoints() {
        val openWith = EngineInfo.UnrealOpenType.Uproject
        val engine = unrealInfo.testingEngines.find { it.id == "4.27" && it.isInstalledBuild }!!
        unrealInTestSetup(openWith, engine)

        testDebugProgram({
            toggleBreakpoint("TestPuzzleProjectBlock.cpp", 34)
        }, {
            waitForPause()
            dumpFullCurrentData()

            toggleBreakpoint("TestPuzzleProjectBlock.cpp", 45)

            toggleBreakpoint("TestPuzzleProjectBlock.cpp", 46)
            toggleBreakpoint("TestPuzzleProjectBlock.cpp", 46)

            resumeSession()
            waitForPause()
            dumpFullCurrentData()
            resumeSession()
        })
    }

    fun testDebugProgram(beforeRun: ExecutionEnvironment.() -> Unit, test: DebugTestExecutionContext.() -> Unit, exitProcessAfterTest: Boolean = false){
        withRunConfigurationEditorWithFirstConfiguration<RiderRunConfigurationBase, SettingsEditor<RiderRunConfigurationBase>>(project) { }
        testDebugProgram(testGoldFile, beforeRun, test, {}, exitProcessAfterTest)
    }

    fun testDebugProgram(
        testFile: File, beforeRun: ExecutionEnvironment.() -> Unit,
        test: DebugTestExecutionContext.() -> Unit, outputConsumer: (String) -> Unit, exitProcessAfterTest: Boolean
    ) {
        testDebugProgram(project, testFile, beforeRun, test, outputConsumer, exitProcessAfterTest)
    }

    fun toggleBreakpoint(projectFile: String, lineNumber: Int): XLineBreakpoint<out XBreakpointProperties<*>>? {
        return toggleBreakpoint(project, projectFile, lineNumber)
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
            openSolutionParams.minimalCountProjectsMustBeLoaded = 1400 // TODO: replace the magic number with something normal
        }

        project = openProject(openWith)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)
    }
}