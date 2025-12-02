package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.ideaInterop.vfs.VfsWriteOperationsHost
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.RdFsRefreshRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.Subsystem
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.annotations.report.Feature
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.unreal.UnrealProjectModelApiFacade
import com.jetbrains.rider.test.framework.combine
import com.jetbrains.rider.test.framework.waitBackendAndWorkspaceModel
import com.jetbrains.rider.test.scriptingApi.copyAdditionalPluginToProject
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import com.jetbrains.rider.test.unreal.UnrealTestLevelProject
import org.testng.annotations.Test
import java.time.Duration

@Subsystem("UnrealLink")
@Feature("Refresh Solution")
@TestSettings(buildTool = BuildTool.UNREAL, sdkVersion = SdkVersion.DOT_NET_8, additionalSdkVersions = [SdkVersion.DOT_NET_6])
@TestEnvironment(platform = [PlatformType.WINDOWS_X64])
class RefreshSolution : UnrealTestLevelProject() {

  @Solution(RiderTestSolution.Unreal.EmptyUProject)
  @Test(dataProvider = "AllEngines_slnOnly")
  @Suppress("UNUSED_PARAMETER")
  fun refreshSolution(caseName: String, openWith: UnrealProjectModelApiFacade.PMType, engine: UnrealEngine) {
    withDump {
      dumpProfile.dumpDirList.clear()
      dumpProfile.dumpDirList.add(activeSolutionDirectory.resolve("Intermediate/ProjectFiles"))
      dumpProfile.fileNames.add("$activeSolution.vcxproj.filters")

      dumpAfterAction("Init") {}
      dumpAfterAction("Copy TestPlugin to project") {
        copyAdditionalPluginToProject("EmptyTestPlugin")
      }

      dumpAfterAction("Invoking refresh solution") {
        project.solution.rdRiderModel.refreshProjects.fire()
        waitPumping(Duration.ofSeconds(1))
        waitAndPump(Duration.ofSeconds(30), { !project.solution.rdRiderModel.refreshInProgress.value },
                    { "Response from UBT took longer than expected time" })

        VfsWriteOperationsHost.getInstance(project).refreshPaths(
          RdFsRefreshRequest(listOf(activeSolutionDirectory.combine("Intermediate", "ProjectFiles", "$activeSolution.vcxproj").toRd()),
                             false))
        waitPumping(Duration.ofSeconds(1))
        waitBackendAndWorkspaceModel(project)
      }
    }
  }
}