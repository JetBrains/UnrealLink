package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions.installRiderLink
import com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions.needInstallRiderLink
import com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions.placeToInstallRiderLink
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.Mutes
import com.jetbrains.rider.test.annotations.RiderTestTimeout
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.contexts.UnrealTestContext
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.framework.getLoadedProjects
import com.jetbrains.rider.test.scriptingApi.buildWithChecks
import com.jetbrains.rider.test.scriptingApi.setConfigurationAndPlatform
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.scriptingApi.withRunProgram
import com.jetbrains.rider.test.unreal.UnrealTestLevelProject
import com.jetbrains.rider.test.unreal.UnrealTestingEngineList
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

@Epic("UnrealLink")
@Feature("Installation")
@TestEnvironment(
  buildTool = BuildTool.CPP,
  sdkVersion = SdkVersion.AUTODETECT
)
class UnrealLinkInstallation : UnrealTestLevelProject() {
  init {
    projectDirectoryName = "EmptyUProject"
  }

  override fun updateUnrealContext(unrealContext: UnrealTestContext) {
    unrealContext.disableEnginePlugins = false
  }

  private val runProgramTimeout: Duration = Duration.ofMinutes(10)

  @Mutes([
           Mute("RIDER-86732", specificParameters = [
             "SlnEngine5_2_Src", "UprojectEngine5_2_Src", "SlnGame5_2_Src", "UprojectGame5_2_Src"]),
         ])
  @Test(dataProvider = "AllEngines_AllPModels")
  @RiderTestTimeout(30L, TimeUnit.MINUTES)
  fun ul(
    @Suppress("UNUSED_PARAMETER") caseName: String,
    openWith: UnrealTestContext.UnrealProjectModelType,
    engine: UnrealEngine,
    location: PluginInstallLocation
  ) {
    placeToInstallRiderLink = location
    needInstallRiderLink = true
    println("RiderLink will be installed in $location")

    getLoadedProjects(project)
    waitAndPump(Duration.ofSeconds(15),
                { project.solution.rdRiderModel.isUnrealEngineSolution.value }, { "This is not unreal solution" })

    setConfigurationAndPlatform(project, "Development Editor", "Win64")

    if (needInstallRiderLink) {
      installRiderLink(placeToInstallRiderLink)
    }

    buildWithChecks(
      project, BuildSolutionAction(), "Build solution",
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
  override fun generateUnrealDataProvider(unrealPmTypes: Array<UnrealTestContext.UnrealProjectModelType>,
                                                    predicate: (UnrealEngine) -> Boolean): MutableIterator<Array<Any>> {
    val types = if (!SystemInfo.isWindows) arrayOf(UnrealTestContext.UnrealProjectModelType.Uproject) else unrealPmTypes
    val result: ArrayList<Array<Any>> = arrayListOf()

    UnrealTestingEngineList.testingEngines.filter(predicate).forEach { engine ->
      val locations = mutableListOf(PluginInstallLocation.Game, PluginInstallLocation.Engine)
      //if (TestFrameworkSettings.Unreal.engineType != "egs") locations.add(PluginInstallLocation.Engine)

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