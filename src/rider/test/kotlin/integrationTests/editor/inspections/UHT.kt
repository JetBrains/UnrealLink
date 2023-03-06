package integrationTests.editor.inspections

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.combine
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.build
import com.jetbrains.rider.test.scriptingApi.checkSwea
import com.jetbrains.rider.test.scriptingApi.dumpProblems
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject

@Epic("Inspections")
@Feature("UHT")
@TestEnvironment(
    platform = [PlatformType.WINDOWS_X64], 
    toolset = ToolsetVersion.TOOLSET_16_CPP, 
    coreVersion = CoreVersion.LATEST_STABLE
)
class UHT : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }

    @BeforeMethod(dependsOnMethods = ["putSolutionToTempDir"])
    override fun prepareAndOpenSolution(parameters: Array<Any>) {
        val openSolutionWithParam = parameters[1] as EngineInfo.UnrealOpenType
        val engineParam = parameters[2] as UnrealEngine
        frameworkLogger.info("Starting open solution (uproject)...")
        prepareUnrealProject(openSolutionWithParam, engineParam)

        testDataDirectory.combine("additionalSource", "files", "UHTInspections").listFiles()?.forEach { file ->
            file.copyTo(activeSolutionDirectory.resolve("$activeSolutionDirectory/Source/$projectDirectoryName/${file.name}"))
        }

        project = openProject(openSolutionWithParam)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)
    }

    @Test(enabled = false, dataProvider = "ue51Only_uprojectOnly")
    fun eteCheck(@Suppress("UNUSED_PARAMETER") caseName: String,
                 openWith: EngineInfo.UnrealOpenType,
                 engine: UnrealEngine) {
        build(project, BuildSolutionAction(), useIncrementalBuild = true, timeout = buildTimeout)
        executeWithGold(testGoldFile) {printStream -> 
            printStream.append(dumpProblems(project))
        }
        checkSwea(project, 0)
    }
}