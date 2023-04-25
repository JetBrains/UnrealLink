package integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.framework.TestProjectModelContext
import com.jetbrains.rider.test.scriptingApi.*
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.io.File
import java.time.Duration

@Epic("UnrealLink")
@Feature("Refresh Solution")
@TestEnvironment(
    platform = [PlatformType.WINDOWS_X64],
    buildTool = BuildTool.CPP,
    sdkVersion = SdkVersion.AUTODETECT
)
@Mute("Incorrect tests")
@Test(dataProvider = "AllEngines_slnOnly")
class RefreshSolution : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }

    fun refreshSolution(@Suppress("UNUSED_PARAMETER") caseName: String,
                        openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        testProjectModel(testGoldFile, project) {
            profile.customPathsToMask = unrealPathsToMask
            profile.customRegexToMask = unrealRegexToMask

            dump("Init") {}
            dump("Invoking refresh solution") {
                createUnrealPlugin(project, "TestNewPluginProject",
                    calculateRootPathInSolutionExplorer(activeSolution, openWith),
                    PluginTemplateType.UNREAL_PLUGIN_BLANK)

                project.solution.rdRiderModel.refreshProjects.fire()
                // Crutch. TODO: Replace with true waiting for UBT to complete it's job
                if (engine.isInstalledBuild)
                    waitPumping(Duration.ofSeconds(8))
                else
                    waitPumping(Duration.ofSeconds(15))

                waitForProjectModelReady(project)
                val vcxprojFiltersReader = File(tempTestDirectory, "$projectDirectoryName/Intermediate/ProjectFiles/$activeSolution.vcxproj.filters")

                assert(vcxprojFiltersReader.readText().contains("TestNewPluginProject"))
            }
        }
    }

    private fun TestProjectModelContext.dump(
        caption: String,
        checkSlnFile: Boolean = false,
        checkIndex: Boolean = false,
        action: () -> Unit
    ) {
        dump(caption, project, activeSolutionDirectory, checkSlnFile, checkIndex, action)
    }

}