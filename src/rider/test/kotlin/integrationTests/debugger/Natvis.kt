package integrationTests.debugger

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rd.platform.diagnostics.LogTraceScenario
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.diagnostics.LogTraceScenarios
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RiderRunConfigurationBase
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.io.File
import java.time.Duration

@TestEnvironment(
    platform = [PlatformType.WINDOWS],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class Natvis : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
        openSolutionParams.waitForCaches = true
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(150)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(150)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)
    }

    override val traceScenarios: Set<LogTraceScenario>
        get() = setOf(LogTraceScenarios.Debugger)

    override val traceCategories: List<String>
        get() = listOf("#com.jetbrains.cidr.execution.debugger")

    @BeforeMethod
    override fun testSetup(){
        super.testSetup()
        File(testCaseSourceDirectory, "UnrealNatvisTestPlugin")
            .copyRecursively(activeSolutionDirectory.resolve("Plugins").resolve("UnrealNatvisTestPlugin"))
    }

    @Test(dataProvider = "enginesAndOthers")
    fun unrealBuiltinNatvis(@Suppress("UNUSED_PARAMETER") caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInTestSetup(openWith, engine)

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")
        buildWithChecks(project, BuildSolutionAction(), "Build solution", useIncrementalBuild = false)

        testDebugProgram(
            {
                toggleBreakpoint("NatvisTest.cpp", 28)
                toggleBreakpoint("NatvisTest.cpp", 39)
                toggleBreakpoint("NatvisTest.cpp", 48)
                toggleBreakpoint("NatvisTest.cpp", 55)
                toggleBreakpoint("NatvisTest.cpp", 64)
                toggleBreakpoint("NatvisTest.cpp", 74)
                toggleBreakpoint("NatvisTest.cpp", 83)
                toggleBreakpoint("NatvisTest.cpp", 93)
                toggleBreakpoint("NatvisTest.cpp", 100)
                toggleBreakpoint("NatvisTest.cpp", 111)
                toggleBreakpoint("NatvisTest.cpp", 118)
                toggleBreakpoint("NatvisTest.cpp", 164)
                toggleBreakpoint("NatvisTest.cpp", 172)
                toggleBreakpoint("NatvisTest.cpp", 186)
                toggleBreakpoint("NatvisTest.cpp", 221)
                toggleBreakpoint("NatvisTest.cpp", 234)
                toggleBreakpoint("NatvisTest.cpp", 244)
                toggleBreakpoint("NatvisTest.cpp", 254)
                toggleBreakpoint("NatvisTest.cpp", 268)
                toggleBreakpoint("NatvisTest.cpp", 282)
                toggleBreakpoint("NatvisTest.cpp", 291)
                toggleBreakpoint("NatvisTest.cpp", 303)
                //toggleBreakpoint("NatvisTest.cpp", 355)
            }, {
                dumpProfile.customRegexToMask["<address>"] = Regex("0x[\\da-fA-F]{16}")
                for (i in 0..21) {
                    waitForCidrPause()
                    dumpFullCurrentData()
                    resumeSession()
                }
            }, exitProcessAfterTest = true)
    }

    fun testDebugProgram(beforeRun: ExecutionEnvironment.() -> Unit, test: DebugTestExecutionContext.() -> Unit, exitProcessAfterTest: Boolean = false){
        withRunConfigurationEditorWithFirstConfiguration<RiderRunConfigurationBase,
                SettingsEditor<RiderRunConfigurationBase>>(project) { }
        testDebugProgram(project, testGoldFile, beforeRun, test, {}, exitProcessAfterTest)
    }

    fun toggleBreakpoint(projectFile: String, lineNumber: Int): XLineBreakpoint<out XBreakpointProperties<*>>? {
        return toggleBreakpoint(project, projectFile, lineNumber)
    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, engine: UnrealEngine ->
            "$baseString${engine.id.replace('.', '_')}"
        }

        val openWith = EngineInfo.UnrealOpenType.Sln
        val egs5: UnrealEngine = unrealInfo.testingEngines.find { it.id == "5.0" && it.isInstalledBuild }!!
        val egs4: UnrealEngine = unrealInfo.testingEngines.find { it.id == "4.27" && it.isInstalledBuild }!!

        result.add(arrayOf(uniqueDataString("$openWith", egs5), openWith, egs5))
        result.add(arrayOf(uniqueDataString("$openWith", egs4), openWith, egs4))

        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }
}