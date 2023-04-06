package integrationTests.unitTesting

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.testFramework.ProjectViewTestUtil
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.Mutes
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.debugger.XDebuggerTestHelper
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.scriptingApi.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.time.Duration

@Epic("Project Model")
@Feature("New Unreal Module")
@TestEnvironment(platform = [PlatformType.WINDOWS_X64], buildTool = BuildTool.CPP, sdkVersion = SdkVersion.AUTODETECT)
class UnitTesting : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
        disableEngineIndexing = true
        disableEnginePlugins = false
    }

    @Test(dataProvider = "AllEngines_uprojectOnly")
    fun runSimpleUT(caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        ProjectViewTestUtil.setupImpl(project, true)

        val path = calculateProjectPathInSolutionExplorer(activeSolution, openWith)
        addNewItem(project, path, TemplateType.UNREAL_SIMPLE_TEST, "SimpleUnitTest")

        withUtFacade(project) { ut ->
            ut.activateExplorer()
            ut.waitForDiscovering()
            ut.runAllTestsInProject(activeSolution, 5, Duration.ofSeconds(90), 5)
        }
    }

    @Test(dataProvider = "AllEngines_uprojectOnly")
    fun debugSimpleUT(caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        ProjectViewTestUtil.setupImpl(project, true)

        val path = calculateProjectPathInSolutionExplorer(activeSolution, openWith)
        addNewItem(project, path, TemplateType.UNREAL_SIMPLE_TEST, "SimpleUnitTest")

        debugUnitTests(project, testGoldFile, { ut ->
            XDebuggerTestHelper.toggleBreakpoint(
                project,
                getVirtualFileFromPath(
                    activeSolutionDirectory.resolve("Source")
                        .resolve(activeSolution).resolve("SimpleUnitTest.cpp").absolutePath ),
                7)
//            toggleBreakpoint(project, vFile, 8)
            ut.createNewSession()
        }) {
            dumpProfile.customRegexToMask["<address>"] = Regex("0x[\\da-fA-F]{16}")
            waitForCidrPause()
            dumpFullCurrentData()
            cidrStepOver()
            dumpFullCurrentData()
        }
    }

    @Test(dataProvider = "AllEngines_uprojectOnly")
    fun runComplexUT(caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        ProjectViewTestUtil.setupImpl(project, true)

        val path = calculateProjectPathInSolutionExplorer(activeSolution, openWith)
        addNewItem(project, path, TemplateType.UNREAL_COMPLEX_TEST, "ComplexUnitTest")
        // hack for unreal unit complex test. Fixed in product at 231
        val editor = withOpenedEditor(project,
            activeSolutionDirectory.resolve("Source")
                .resolve(activeSolution).resolve("ComplexUnitTest.cpp").absolutePath
        ) {
            this.insertString(8, 0, """
                OutBeautifiedNames.Add("");
	            OutTestCommands.Add("");
            """.trimIndent())
        }
        closeEditor(editor)

        withUtFacade(project) { ut ->
            ut.waitForDiscovering()
            ut.runAllTestsInProject(activeSolution, 5, Duration.ofSeconds(90), 5)
        }
    }

    @Test(dataProvider = "AllEngines_uprojectOnly")
    fun discoverUT(caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        ProjectViewTestUtil.setupImpl(project, true)

        val path = calculateProjectPathInSolutionExplorer(activeSolution, openWith)
        addNewItem(project, path, TemplateType.UNREAL_SIMPLE_TEST, "SimpleUnitTest")

        withUtFacade(project) { ut ->
            ut.activateExplorer()
            ut.waitForDiscovering()
            val session = ut.createNewSession()
            ut.selectNodeByName(session, "SimpleUnitTest")
            ut.compareSessionTreeWithGold(session, testGoldFile)
        }
    }
}