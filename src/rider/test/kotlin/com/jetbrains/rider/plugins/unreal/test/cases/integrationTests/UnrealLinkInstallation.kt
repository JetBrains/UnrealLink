package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.RiderTestTimeout
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.Subsystem
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.annotations.report.Feature
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.Mono
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.setUnrealConfigurationAndPlatform
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.scriptingApi.withRunProgram
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import com.jetbrains.rider.test.unreal.UnrealConstants
import com.jetbrains.rider.test.unreal.UnrealEnvironment
import com.jetbrains.rider.test.unreal.UnrealTestCombinations
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.lang.reflect.Method
import java.time.Duration
import java.util.concurrent.TimeUnit

@Subsystem("UnrealLink")
@Feature("Installation")
@TestSettings(buildTool = BuildTool.UNREAL, mono = Mono.UNIX_ONLY, sdkVersion = SdkVersion.DOT_NET_8, additionalSdkVersions = [SdkVersion.DOT_NET_6])
class UnrealLinkInstallation : UnrealLinkBase() {
  private val runProgramTimeout: Duration = Duration.ofMinutes(10)

  /**
   * Extends standard [UnrealTestCombinations] with an additional [PluginInstallLocation] dimension.
   * Produces (env, location) pairs — engine and openMode are applied automatically by
   * [com.jetbrains.rider.test.unreal.UnrealBase.applyDataProviderCombination], only location is
   * test-specific.
   */
  @DataProvider(name = "unrealLinkCombinations")
  fun unrealLinkCombinations(method: Method): Array<Array<Any>> {
    val combinations = UnrealTestCombinations.combinations(method)
    val locations = listOf(PluginInstallLocation.Game, PluginInstallLocation.Engine)

    val result = combinations.flatMap { (engine, openMode) ->
      locations.map { location ->
        arrayOf(UnrealEnvironment(engine, openMode) as Any, location as Any)
      }
    }.toTypedArray()

    frameworkLogger.info("unrealLinkCombinations for ${method.name}:" +
            "combinations=${combinations.size}, " +
            "locations=${locations.size}, total=${result.size}")

    return result
  }

  @BeforeMethod
  fun setOpenSolutionSettings() {
    unrealApiFacade.disableEnginePlugins = false
  }

  @Solution(RiderTestSolution.Unreal.EmptyUProject)
  @Test(dataProvider = "unrealLinkCombinations")
  @RiderTestTimeout(10, TimeUnit.MINUTES)
  fun ul(env: UnrealEnvironment, location: PluginInstallLocation) {
    setUnrealConfigurationAndPlatform(project, UnrealConstants.UnrealConfigurations.DevelopmentEditor)

    installRiderLink(location)

    buildStartupProject()

    withRunProgram(project, configurationName = activeSolution) {
      waitAndPump(runProgramTimeout, { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })
      waitPumping(Duration.ofSeconds(15))
    }
  }
}