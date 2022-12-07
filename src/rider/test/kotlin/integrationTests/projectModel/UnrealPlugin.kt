package integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.build.actions.BuildSolutionAction
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
@Feature("New Unreal Plugin")
@TestEnvironment(
    platform = [PlatformType.WINDOWS_ALL],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class UnrealPlugin : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }

    @Test(dataProvider = "egsOnly_AllPModels")
    fun newPlugin(@Suppress("UNUSED_PARAMETER") caseName: String,
                  openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        testProjectModel(testGoldFile, project) {
            profile.customPathsToMask = unrealPathsToMask
            profile.customRegexToMask = unrealRegexToMask

            dump("Init") {}
            dump("Add plugin to '$activeSolution' via project node") {
                createUnrealPlugin(project, "TestNewPluginProject",
                                   calculateRootPathInSolutionExplorer(activeSolution, openWith),
                                   PluginTemplateType.UNREAL_PLUGIN_BLANK)
            }
            activeSolutionDirectory.resolve("Plugins").resolve("TestNewPluginProject").exists()
        }
    }

    private fun TestProjectModelContext.dump(
        caption: String,
        checkSlnFile: Boolean = false,
        checkIndex: Boolean = false,
        action: () -> Unit
    ) {
        dump(caption, project, activeSolutionDirectory.resolve("Plugins"), checkSlnFile, checkIndex, action)
    }
}