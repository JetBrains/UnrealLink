package integrationTests.debugger

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.platform.diagnostics.LogTraceScenario
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.diagnostics.LogTraceScenarios
import com.jetbrains.rider.run.configurations.RiderRunConfigurationBase
import com.jetbrains.rider.test.allure.Subsystem
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.combine
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.time.Duration

@Epic(Subsystem.DEBUGGER)
@Feature("Breakpoints")
@TestEnvironment(
    platform = [PlatformType.WINDOWS],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class BreakpointBase : UnrealTestProject() {
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
        testDataDirectory.combine("additionalSource", "plugins", "DebugTestPlugin")
            .copyRecursively(activeSolutionDirectory.resolve("Plugins").resolve("DebugTestPlugin"))
    }

    @Test(dataProvider = "enginesAndOthers")
    fun toggleTest(@Suppress("UNUSED_PARAMETER") caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInTestSetup(openWith, engine)

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")
        buildWithChecks(project, BuildSolutionAction(), "Build solution", useIncrementalBuild = false)

        testDebugProgram(
            {
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 12)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 17)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 28)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 30)
            }, {
                dumpProfile.customRegexToMask["<address>"] = Regex("0x[\\da-fA-F]{16}")

                waitForCidrPause()                  // 28: someNumber = Foo(someNumber);
                dumpFullCurrentData()
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 30)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 30)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 32)
                resumeSession()
                waitForCidrPause()                  // 12: return fooNum * 2;
                dumpFullCurrentData()

                toggleBreakpoint(project, "DebugTestPlugin.cpp", 31)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 31)

                resumeSession()
                waitForCidrPause()                  // 17: return b * 3;
                dumpFullCurrentData()

                resumeSession()
                waitForCidrPause()                  // 30: someNumber = Moo(someNumber);
                dumpFullCurrentData()

                resumeSession()
                waitForCidrPause()                  // 12: return fooNum * 2;
                dumpFullCurrentData()

                resumeSession()
                waitForCidrPause()                  // 32: }
                dumpFullCurrentData()
            },
            exitProcessAfterTest = true
        )
    }

    fun testDebugProgram(beforeRun: ExecutionEnvironment.() -> Unit, test: DebugTestExecutionContext.() -> Unit, exitProcessAfterTest: Boolean = false){
        withRunConfigurationEditorWithFirstConfiguration<RiderRunConfigurationBase, SettingsEditor<RiderRunConfigurationBase>>(project) {}
        testDebugProgram(project, testGoldFile, beforeRun, test, {}, exitProcessAfterTest)
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