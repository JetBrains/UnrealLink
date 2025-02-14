package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.build.actions.BuildStartupProject
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.*
import com.jetbrains.rider.test.contexts.UnrealTestContext
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.buildWithChecks
import com.jetbrains.rider.test.scriptingApi.setUnrealConfigurationAndPlatform
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.scriptingApi.withRunProgram
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import com.jetbrains.rider.test.unreal.UnrealConstants
import com.jetbrains.rider.test.unreal.UnrealTestingEngineList
import org.testng.annotations.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

@Mute("RIDER-121226", specificParameters = ["SlnEngine5_4, SlnGame5_4, UprojectGame5_4, UprojectEngine5_4"])
@Subsystem("UnrealLink")
@Feature("Installation")
@TestEnvironment(buildTool = BuildTool.UNREAL, sdkVersion = SdkVersion.DOT_NET_8, additionalSdkVersions = [SdkVersion.DOT_NET_6])
class UnrealLinkInstallation : UnrealLinkBase() {
  private val runProgramTimeout: Duration = Duration.ofMinutes(10)
  override fun updateUnrealContext(unrealContext: UnrealTestContext) {
    unrealContext.disableEnginePlugins = false
  }

  @Solution(RiderTestSolution.Unreal.EmptyUProject)
  @Test(dataProvider = "AllEngines_AllPModels")
  @RiderTestTimeout(30L, TimeUnit.MINUTES)
  fun ul(
    @Suppress("UNUSED_PARAMETER") caseName: String,
    openWith: UnrealTestContext.UnrealProjectModelType,
    engine: UnrealEngine,
    location: PluginInstallLocation
  ) {
    setUnrealConfigurationAndPlatform(project, UnrealConstants.UnrealConfigurations.DevelopmentEditor)
    
    installRiderLink(location)

    buildWithChecks(
      project, BuildStartupProject(), "Build selected projects",
      useIncrementalBuild = false, timeout = contexts.get<UnrealTestContext>().unrealBuildTimeout
    )
    //        checkThatBuildArtifactsExist(project)  // TODO create checker for unreal projects

    withRunProgram(project, configurationName = activeSolution) {
      waitAndPump(runProgramTimeout, { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })
      waitPumping(Duration.ofSeconds(15))
    }
  }

  /**
   * [UnrealLinkInstallation] have additional parameter - location ([PluginInstallLocation]), so we need to override
   * data provider generating.
   */
  override fun generateUnrealFullDataProvider(unrealPmTypes: Array<UnrealTestContext.UnrealProjectModelType>,
                                              predicate: (UnrealEngine) -> Boolean): MutableIterator<Array<Any>> {
    val types = if (!SystemInfo.isWindows) arrayOf(UnrealTestContext.UnrealProjectModelType.Uproject) else unrealPmTypes
    val result: ArrayList<Array<Any>> = arrayListOf()

    UnrealTestingEngineList.testingEngines.filter(predicate).forEach { engine ->
      val locations = mutableListOf(PluginInstallLocation.Game, PluginInstallLocation.Engine)
      locations.forEach { location ->
        types.forEach { type ->
          // Install RL in UE5 in Engine breaks project build. See https://jetbrains.slack.com/archives/CH506NL5P/p1622199704007800 TODO?
          // if ((engine.version.major == 5) && engine.isInstalledBuild && location == PluginInstallLocation.Engine) return@forEach
          result.add(arrayOf(uniqueDataString("$type$location", engine), type, engine, location))
        }
      }
    }
    frameworkLogger.debug("Data Provider was generated: $result")
    return result.iterator()
  }

  override val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, engine: UnrealEngine ->
    // If we use engine from source, it's ID is GUID, so we replace it by 'normal' id plus ".fromSouce" string
    // else just replace dots in engine version, 'cause of part after last dot will be parsed as file type.
    if (engine.id.matches(guidRegex)) "$baseString${engine.version.major}_${engine.version.minor}_Src"
    else "$baseString${engine.id.replace('.', '_')}"
  }
}