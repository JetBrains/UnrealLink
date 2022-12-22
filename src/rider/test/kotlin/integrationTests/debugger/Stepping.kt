package integrationTests.debugger

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.platform.diagnostics.LogTraceScenario
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.diagnostics.LogTraceScenarios
import com.jetbrains.rider.run.configurations.RiderRunConfigurationBase
import com.jetbrains.rider.test.allure.Subsystem
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.combine
import com.jetbrains.rider.test.scriptingApi.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject

@Epic(Subsystem.DEBUGGER)
@Feature("Stepping")
@TestEnvironment(
    platform = [PlatformType.WINDOWS_X64],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
@Mute
class Stepping : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }

    override val traceScenarios: Set<LogTraceScenario>
        get() = setOf(LogTraceScenarios.Debugger)

    override val traceCategories: List<String>
        get() = listOf("#com.jetbrains.cidr.execution.debugger")

    @BeforeMethod
    override fun prepareAndOpenSolution(parameters: Array<Any>) {
        testDataDirectory.combine("additionalSource", "plugins", "DebugTestPlugin")
            .copyRecursively(activeSolutionDirectory.resolve("Plugins").resolve("DebugTestPlugin"))
        super.prepareAndOpenSolution(parameters)
    }

    @Test(dataProvider = "egsOnly_SlnOnly")
    fun stepping(@Suppress("UNUSED_PARAMETER") caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")
        buildWithChecks(project, BuildSolutionAction(), "Build solution",
            useIncrementalBuild = false, timeout = buildTimeout)

        testDebugProgram(
            {
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 28)
            }, {
                dumpProfile.customRegexToMask["<address>"] = Regex("0x[\\da-fA-F]{16}")

                waitForCidrPause()
                dumpFullCurrentData()   // 28: someNumber = Foo(someNumber);
                cidrStepInto()
                cidrStepOver()
                cidrStepOver()
                dumpFullCurrentData()   // 12: return fooNum * 2;
                cidrStepOut()
                dumpFullCurrentData()   // 28: someNumber = Foo(someNumber);
                cidrStepInto()
                cidrStepInto()
                cidrStepInto()
                dumpFullCurrentData()   // 17: return b * 3;
                cidrStepOver()
                cidrStepOver()
                cidrStepOver()
                dumpFullCurrentData()   // 30: someNumber = Moo(someNumber);
                cidrStepInto()
                cidrStepInto()
                cidrStepInto()
                cidrStepInto()
                cidrStepInto()
                dumpFullCurrentData()   // 12: return fooNum * 2;
                cidrStepOut()
                cidrStepOut()
                cidrStepOver()
                dumpFullCurrentData()   // 31: std::cout << someNumber;
            },
            exitProcessAfterTest = true
        )
    }

    private fun testDebugProgram(
        beforeRun: ExecutionEnvironment.() -> Unit,
        test: DebugTestExecutionContext.() -> Unit,
        exitProcessAfterTest: Boolean = false
    ) {
        withRunConfigurationEditorWithFirstConfiguration<RiderRunConfigurationBase,
                SettingsEditor<RiderRunConfigurationBase>>(project) {}
        testDebugProgram(project, testGoldFile, beforeRun, test, {}, exitProcessAfterTest)
    }
}