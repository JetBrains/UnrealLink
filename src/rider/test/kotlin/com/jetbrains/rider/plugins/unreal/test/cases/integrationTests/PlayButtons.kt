package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.*
import com.jetbrains.rider.test.contexts.UnrealTestContext
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.scriptingApi.buildWithChecks
import com.jetbrains.rider.test.scriptingApi.setConfigurationAndPlatform
import com.jetbrains.rider.test.scriptingApi.withRunProgram
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import org.testng.annotations.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

@Subsystem("UnrealLink")
@Feature("PlayButtons")
@TestEnvironment(
  buildTool = BuildTool.CPP,
  sdkVersion = SdkVersion.AUTODETECT
)
@RiderTestTimeout(20, TimeUnit.MINUTES)
class PlayButtons : UnrealLinkBase() {
  override fun updateUnrealContext(unrealContext: UnrealTestContext) {
    unrealContext.disableEnginePlugins = false
  }

  private val runProgramTimeout: Duration = Duration.ofMinutes(10)

  private val context: DataContext get() = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project)
  private val startAction: AnAction get() = ActionManager.getInstance().getAction("RiderLink.StartUnreal")
  private val stepAction: AnAction get() = ActionManager.getInstance().getAction("RiderLink.SingleStepUnreal")
  private val pauseAction: AnAction get() = ActionManager.getInstance().getAction("RiderLink.PauseUnreal")
  private val resumeAction: AnAction get() = ActionManager.getInstance().getAction("RiderLink.ResumeUnreal")
  private val stopAction: AnAction get() = ActionManager.getInstance().getAction("RiderLink.StopUnreal")

  @Solution(RiderTestSolution.Unreal.EmptyUProject)
  @Test(dataProvider = "AllEngines_AllPModels")
  @Mute("RIDER-102094 UnrealLink tests' build fail against UE 5.3", specificParameters = ["Sln5_3"])
  fun endToEndTest(
    @Suppress("UNUSED_PARAMETER") caseName: String,
    openWith: UnrealTestContext.UnrealProjectModelType,
    engine: UnrealEngine
  ) {
    setConfigurationAndPlatform(project, "Development Editor", "Win64")
    installRiderLink(PluginInstallLocation.Game)

    buildWithChecks(
      project, BuildSolutionAction(), "Build solution",
      useIncrementalBuild = false, timeout = contexts.get<UnrealTestContext>().unrealBuildTimeout
    )

    checkActionsIsEnabled(mapOf(
      startAction to false,
      stepAction to false,
      pauseAction to false,
      resumeAction to false,
      stopAction to false
    ))

    checkActionsIsVisible(mapOf(
      startAction to true,
      stepAction to false,
      pauseAction to true,
      resumeAction to false,
      stopAction to true
    ))

    withRunProgram(project, configurationName = activeSolution) { // open Unreal Editor
      waitAndPump(runProgramTimeout,
                  { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })

      checkActionsIsEnabled(mapOf(
        startAction to true,
        stepAction to false,
        pauseAction to false,
        resumeAction to false,
        stopAction to false
      ))

      checkActionsIsVisible(mapOf(
        startAction to true,
        stepAction to false,
        pauseAction to true,
        resumeAction to false,
        stopAction to true
      ))

      pushUnrealButton(startAction)

      checkActionsIsEnabled(mapOf(
        startAction to false,
        stepAction to false,
        pauseAction to true,
        resumeAction to false,
        stopAction to true
      ))

      checkActionsIsVisible(mapOf(
        startAction to false,
        stepAction to false,
        pauseAction to true,
        resumeAction to true,
        stopAction to true
      ))

      pushUnrealButton(pauseAction)

      checkActionsIsEnabled(mapOf(
        startAction to false,
        stepAction to true,
        pauseAction to false,
        resumeAction to true,
        stopAction to true
      ))

      checkActionsIsVisible(mapOf(
        startAction to false,
        stepAction to true,
        pauseAction to false,
        resumeAction to true,
        stopAction to true
      ))

      pushUnrealButton(resumeAction)

      checkActionsIsEnabled(mapOf(
        startAction to false,
        stepAction to false,
        pauseAction to true,
        resumeAction to false,
        stopAction to true
      ))

      checkActionsIsVisible(mapOf(
        startAction to false,
        stepAction to false,
        pauseAction to true,
        resumeAction to true,
        stopAction to true
      ))

      pushUnrealButton(stopAction)

      checkActionsIsEnabled(mapOf(
        startAction to true,
        stepAction to false,
        pauseAction to false,
        resumeAction to false,
        stopAction to false
      ))

      checkActionsIsVisible(mapOf(
        startAction to true,
        stepAction to false,
        pauseAction to true,
        resumeAction to false,
        stopAction to true
      ))
    } // close Unreal Editor

    checkActionsIsEnabled(mapOf(
      startAction to false,
      stepAction to false,
      pauseAction to false,
      resumeAction to false,
      stopAction to false
    ))

    checkActionsIsVisible(mapOf(
      startAction to true,
      stepAction to false,
      pauseAction to true,
      resumeAction to false,
      stopAction to true
    ))

  }

  fun createEvent(action: AnAction): AnActionEvent {
    val event = AnActionEvent.createFromAnAction(action, null, "", context)
    action.update(event)
    return event
  }

  fun checkActionsIsEnabled(actions: Map<AnAction, Boolean>) {
    actions.forEach { (action, isEnabled) ->
      val event = createEvent(action)
      pumpMessages(timeout = Duration.ofSeconds(5)) {
        action.update(event)
        if (isEnabled) event.presentation.isEnabled else !event.presentation.isEnabled
      }
    }
  }

  fun checkActionsIsVisible(actions: Map<AnAction, Boolean>) {
    actions.forEach { (action, isVisible) ->
      val event = createEvent(action)
      pumpMessages(timeout = Duration.ofSeconds(5)) {
        action.update(event)
        if (isVisible) event.presentation.isVisible else !event.presentation.isVisible
      }
    }
  }

  private fun pushUnrealButton(action: AnAction) = action.actionPerformed(createEvent(action))
}