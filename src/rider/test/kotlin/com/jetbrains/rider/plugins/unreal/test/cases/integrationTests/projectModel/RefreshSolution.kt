package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.ideaInterop.vfs.VfsWriteOperationsHost
import com.jetbrains.rider.model.RdFsRefreshRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.report.Feature
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.Subsystem
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.contexts.ProjectModelTestContext
import com.jetbrains.rider.test.contexts.UnrealTestContext
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.framework.combine
import com.jetbrains.rider.test.framework.waitBackendAndWorkspaceModel
import com.jetbrains.rider.test.scriptingApi.ProjectModelExp.dumpAfterAction
import com.jetbrains.rider.test.scriptingApi.ProjectModelExp.withUnrealDump
import com.jetbrains.rider.test.scriptingApi.copyAdditionalPluginToProject
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import com.jetbrains.rider.test.unreal.UnrealTestLevelProject
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.time.Duration

@Subsystem("UnrealLink")
@Feature("Refresh Solution")
@TestEnvironment(
  platform = [PlatformType.WINDOWS_X64],
  buildTool = BuildTool.UNREAL,
  sdkVersion = SdkVersion.DOT_NET_8,
  additionalSdkVersions = [SdkVersion.DOT_NET_6]
)
class RefreshSolution : UnrealTestLevelProject() {

  @BeforeMethod
  fun updateDumpProfile() {
    contexts.get<ProjectModelTestContext>().profile.dumpDirList.clear()
    contexts.get<ProjectModelTestContext>().profile.dumpDirList.add(activeSolutionDirectory.resolve("Intermediate/ProjectFiles"))
    contexts.get<ProjectModelTestContext>().profile.fileNames.add("$activeSolution.vcxproj.filters")
  }

  @Solution(RiderTestSolution.Unreal.EmptyUProject)
  @Test(dataProvider = "AllEngines_slnOnly")
  fun refreshSolution(@Suppress("UNUSED_PARAMETER") caseName: String,
                      openWith: UnrealTestContext.UnrealProjectModelType, engine: UnrealEngine) {
    val pmContext = contexts.get<ProjectModelTestContext>()

    withUnrealDump(contexts) {
      dumpAfterAction("Init", pmContext) {}
      dumpAfterAction("Copy TestPlugin to project", pmContext) {
        copyAdditionalPluginToProject("EmptyTestPlugin")
       }

      dumpAfterAction("Invoking refresh solution", pmContext) {
        project.solution.rdRiderModel.refreshProjects.fire()
        waitPumping(Duration.ofSeconds(1))
        waitAndPump(Duration.ofSeconds(30),
                    { !project.solution.rdRiderModel.refreshInProgress.value },
                    { "Response from UBT took longer than expected time" })

        VfsWriteOperationsHost.getInstance(project).refreshPaths(
          RdFsRefreshRequest(listOf(activeSolutionDirectory.combine("Intermediate", "ProjectFiles", "$activeSolution.vcxproj").path), false)
        )
        waitPumping(Duration.ofSeconds(1))
        waitBackendAndWorkspaceModel(project)
      }
    }
  }
}