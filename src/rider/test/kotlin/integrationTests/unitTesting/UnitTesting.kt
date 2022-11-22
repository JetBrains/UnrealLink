package integrationTests.unitTesting

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ProjectViewTestUtil
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.debugger.XDebuggerTestHelper
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.scriptingApi.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject

@Epic("Project Model")
@Feature("New Unreal Module")
@TestEnvironment(platform = [PlatformType.WINDOWS_X64], toolset = ToolsetVersion.TOOLSET_16_CPP, coreVersion = CoreVersion.DEFAULT)
class UnitTesting : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
        disableEngineIndexing = false
        disableEnginePlugins = false
    }

    @Test(dataProvider = "AllEngines_slnOnly")
    fun runSimpleUT(caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        ProjectViewTestUtil.setupImpl(project, true)

        val path = calculateProjectPathInSolutionExplorer(activeSolution, openWith)
        addNewItem(project, path, TemplateType.UNREAL_SIMPLE_TEST, "SimpleUnitTest")

        withUtFacade(project) { ut ->
            ut.activateExplorer()
            ut.waitForDiscovering()
            ut.runAllTestsInProject(activeSolution, 4, RiderUnitTestScriptingFacade.defaultTimeout, 4)
        }
    }

    @Test(dataProvider = "AllEngines_slnOnly")
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

    @Test(dataProvider = "AllEngines_slnOnly")
    fun runComplexUT(caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        ProjectViewTestUtil.setupImpl(project, true)

        val path = calculateProjectPathInSolutionExplorer(activeSolution, openWith)
        addNewItem(project, path, TemplateType.UNREAL_COMPLEX_TEST, "ComplexUnitTest")

        withUtFacade(project) { ut ->
            ut.waitForDiscovering()
            ut.runAllTestsInProject(activeSolution, 4, RiderUnitTestScriptingFacade.defaultTimeout, 4)
        }
    }

    @Test(dataProvider = "AllEngines_slnOnly")
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