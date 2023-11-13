package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.contexts.ProjectModelTestContext
import com.jetbrains.rider.test.contexts.UnrealTestContext
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.TestProjectModelContext
import com.jetbrains.rider.test.scriptingApi.*
import com.jetbrains.rider.test.scriptingApi.experimental.ProjectModelExp.dumpAfterAction
import com.jetbrains.rider.test.scriptingApi.experimental.ProjectModelExp.withDump
import com.jetbrains.rider.test.unreal.UnrealTestLevelProject
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.time.Duration

@Epic("UnrealLink")
@Feature("Refresh Solution")
@TestEnvironment(
  platform = [PlatformType.WINDOWS_X64],
  buildTool = BuildTool.CPP,
  sdkVersion = SdkVersion.AUTODETECT
)
class RefreshSolution : UnrealTestLevelProject() {
  init {
    projectDirectoryName = "EmptyUProject"
  }

  @BeforeMethod
  fun dumpProjectFiles(){
    contexts.get<ProjectModelTestContext>().profile.dumpDirList.add(activeSolutionDirectory.resolve("Intermediate/ProjectFiles"))
  }

  @Test(dataProvider = "AllEngines_slnOnly")
  fun refreshSolution(@Suppress("UNUSED_PARAMETER") caseName: String,
                      openWith: UnrealTestContext.UnrealProjectModelType, engine: UnrealEngine) {
    val pmContext = contexts.get<ProjectModelTestContext>()

    withDump(contexts) {
      dumpAfterAction("Init", pmContext) {}
      dumpAfterAction("Invoking refresh solution", pmContext) {
        createUnrealPlugin(project, "TestNewPluginProject",
                           calculateUprojectRootPathInSolutionExplorer(activeSolution, openWith),
                           PluginTemplateType.UNREAL_PLUGIN_BLANK)

        project.solution.rdRiderModel.refreshProjects.fire()
        waitForProjectModelReady(project)

        val waitForUBTTimeout = Duration.ofSeconds(30)
        waitAndPump(waitForUBTTimeout,
                    { !project.solution.rdRiderModel.refreshInProgress.value },
                    { "Response from UBT took longer than expected time" })
      }
    }
  }
}