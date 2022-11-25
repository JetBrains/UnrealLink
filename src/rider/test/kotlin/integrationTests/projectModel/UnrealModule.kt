package integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.TestProjectModelContext
import com.jetbrains.rider.test.scriptingApi.*
import com.jetbrains.rider.test.scriptingApi.TemplateType.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.time.Duration

@Epic("Project Model")
@Feature("New Unreal Module")
@TestEnvironment(platform = [PlatformType.WINDOWS_X64], toolset = ToolsetVersion.TOOLSET_16_CPP, coreVersion = CoreVersion.DEFAULT)
class UnrealModule : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }

    private val runProgramTimeout: Duration = Duration.ofMinutes(2)

    @Test(dataProvider = "egsOnly_AllPModels")
    fun newRuntimeModule(@Suppress("UNUSED_PARAMETER") caseName: String,
                         openWith: EngineInfo.UnrealOpenType,
                         engine: UnrealEngine) {
        testProjectModel(testGoldFile, project) {
            profile.customPathsToMask = unrealPathsToMask
            profile.customRegexToMask = unrealRegexToMask

            dump("Init") {}
            dump("Add module to '$activeSolution' via project node") {
                createUnrealModule(project, "TestNewModuleProject", calculateRootPathInSolutionExplorer(activeSolution, openWith))
            }
            dump("Add module to '$activeSolution' via Source node") {
                createUnrealModule(project, "TestNewModuleSource",
                                   calculateRootPathInSolutionExplorer(activeSolution, openWith) + "Source")
            }
            dump("Add module to '$activeSolution' with custom location") {
                createUnrealModule(project, "TestNewModuleCustom", calculateRootPathInSolutionExplorer(activeSolution, openWith),
                                   ModuleDescription(relativePath = "subfolder/subsubfolder"))
            }
        }

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")
        buildWithChecks(
            project, BuildSolutionAction(), "Build solution",
            useIncrementalBuild = false, timeout = buildTimeout
        )
        //        checkThatBuildArtifactsExist(project)  // TODO create checker for unreal projects

        withRunProgram(configurationName = activeSolution) {
            waitAndPump(runProgramTimeout,
                        { activeSolutionDirectory.resolve("Saved").resolve("Logs").resolve("$activeSolution.log").exists() },
                        { "Editor wasn't run!" })
        }
    }

    @Mute("Can not find requested path(s) in tree: \"Engine\". Fixed in 231.")
    @Test(dataProvider = "ue5SourceOnly_AllPModels")
    fun newRuntimeEngineModule(caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        testProjectModel(testGoldFile, project) {
            profile.customPathsToMask = unrealPathsToMask
            profile.customRegexToMask = unrealRegexToMask

            val path = arrayOf("Engine")

            dump("Init") {}
            dump("Add module to '$activeSolution' via Engine node") {
                logger.info("Start adding runtime module")
                createUnrealModule(project, "TestNewModuleProject", path)
            }
        }
    }

    private fun TestProjectModelContext.dump(
        caption: String,
        checkSlnFile: Boolean = false,
        checkIndex: Boolean = false,
        action: () -> Unit
    ) {
        dump(caption, project, activeSolutionDirectory.resolve("Source"), checkSlnFile, checkIndex, action)
    }
}