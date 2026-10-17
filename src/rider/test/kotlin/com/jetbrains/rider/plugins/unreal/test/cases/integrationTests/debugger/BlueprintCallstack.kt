package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests.debugger

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestModeFlags
import com.intellij.xdebugger.frame.XStackFrame
import com.jetbrains.cidr.execution.debugger.CidrDebuggerTestFlags
import com.jetbrains.rider.plugins.unreal.debugger.frames.BlueprintFrame
import com.jetbrains.rider.plugins.unreal.debugger.frames.StubBlueprintFrame
import com.jetbrains.rider.plugins.unreal.debugger.frames.UnrealExternalCodeFrame
import com.jetbrains.rider.plugins.unreal.debugger.frames.UnrealFrameBase
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.test.cases.integrationTests.UnrealLinkClassLevelBase
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings
import com.jetbrains.rider.test.annotations.RiderTestTimeout
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.Subsystem
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.annotations.report.ChecklistItems
import com.jetbrains.rider.test.annotations.report.Feature
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.debugger.XDebuggerTestHelper
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.reporting.SubsystemConstants
import com.jetbrains.rider.test.scriptingApi.DebugTestExecutionContext
import com.jetbrains.rider.test.scriptingApi.copyAdditionalPluginToProject
import com.jetbrains.rider.test.scriptingApi.ignoreSighup
import com.jetbrains.rider.test.scriptingApi.setUnrealConfigurationAndPlatform
import com.jetbrains.rider.test.scriptingApi.text
import com.jetbrains.rider.test.scriptingApi.toggleBreakpointsByPattern
import com.jetbrains.rider.test.scriptingApi.waitForCidrPause
import com.jetbrains.rider.test.scriptingApi.waiter
import com.jetbrains.rider.test.shared.constants.TeamCityTags
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import com.jetbrains.rider.test.unreal.UnrealConstants
import com.jetbrains.rider.test.unreal.testDebugProgramWithoutGold
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Checks the Blueprint frames that [com.jetbrains.rider.plugins.unreal.debugger.BlueprintExecutionStack] adds to the C++ call stack,
 * and the toggles of the frames popup.
 *
 * The `BlueprintCallstackTestPlugin` test plugin creates a Blueprint at editor startup. The Blueprint function
 * `CallNativeFunction` calls the C++ function `UBlueprintCallstackTestLibrary::CalledFromBlueprint`, where the test stops.
 *
 * The feature works only on a Windows host, and it needs the `RiderDebuggerSupport` module of RiderLink.
 */
@Tag(TeamCityTags.GameDev.Unreal.Link.General)
@Subsystem(SubsystemConstants.UNREAL_LINK)
@Feature("Blueprint Callstack")
@RiderTestTimeout(20, TimeUnit.MINUTES)
@TestEnvironment(platform = [PlatformType.WINDOWS_X64])
@Solution(RiderTestSolution.Unreal.EmptyUProject)
class BlueprintCallstack : UnrealLinkClassLevelBase() {
  /** See [com.jetbrains.rider.plugins.unreal.test.cases.integrationTests.PlayButtons.beforeOpenSolution] for why this is not a `@BeforeEach`. */
  override fun beforeOpenSolution() {
    super.beforeOpenSolution()
    unrealApiFacade.disableEnginePlugins = false
    copyAdditionalPluginToProject(TEST_PLUGIN_NAME)
  }

  @Test
  @ChecklistItems(["UnrealLink/Debug/Blueprint Callstack"])
  fun blueprintFramesInCallstack() {
    setUnrealConfigurationAndPlatform(project, UnrealConstants.UnrealConfigurations.DevelopmentEditor)
    installRiderLink(PluginInstallLocation.Game)
    buildStartupProject()

    val settings = UnrealLogPanelSettings.getInstance(project)
    val initialShowBlueprintCallstack = settings.showBlueprintCallstack
    val initialShowUnrealFrames = settings.showUnrealFrames
    // In the unit test mode, CidrExecutionStack computes frames only for a container whose class name contains "XTest".
    // BlueprintExecutionStack wraps the test container in BlueprintStackFrameContainer, so without this flag no frames come.
    val initialBypassViewsUpdates = TestModeFlags.get(CidrDebuggerTestFlags.BYPASS_VIEWS_UPDATES_DISABLED_IN_TESTS)
    TestModeFlags.set(CidrDebuggerTestFlags.BYPASS_VIEWS_UPDATES_DISABLED_IN_TESTS, true)
    try {
      testDebugProgramWithoutGold({
        toggleBreakpointsByPattern(project, "BlueprintCallstackTestLibrary.cpp", "@breakpoint").shouldBe(1)
      }, {
        ignoreSighup()
        waitForCidrPause()

        setToggle(SHOW_BLUEPRINT_FUNCTIONS_ACTION_ID, true)
        setToggle(SHOW_UNREAL_FRAMES_ACTION_ID, true)
        collectFrames().let { frames ->
          assertTopFrameIsBreakpointFunction(frames)
          assertBlueprintFramePresent(frames)
          frames.none { it is UnrealExternalCodeFrame }
            .shouldBeTrue("Unreal Engine frames are collapsed:\n${frames.dump()}")
        }

        setToggle(SHOW_BLUEPRINT_FUNCTIONS_ACTION_ID, false)
        collectFrames().let { frames ->
          assertTopFrameIsBreakpointFunction(frames)
          frames.none { it is BlueprintFrame || it is StubBlueprintFrame }
            .shouldBeTrue("Blueprint frames are shown although the option is off:\n${frames.dump()}")
        }

        setToggle(SHOW_BLUEPRINT_FUNCTIONS_ACTION_ID, true)
        setToggle(SHOW_UNREAL_FRAMES_ACTION_ID, false)
        collectFrames().let { frames ->
          assertTopFrameIsBreakpointFunction(frames)
          assertBlueprintFramePresent(frames)
          frames.any { it is UnrealExternalCodeFrame }
            .shouldBeTrue("Unreal Engine frames are not collapsed:\n${frames.dump()}")
        }
      }, exitProcessAfterTest = true, timeout = Duration.ofMinutes(5))
    }
    finally {
      TestModeFlags.set(CidrDebuggerTestFlags.BYPASS_VIEWS_UPDATES_DISABLED_IN_TESTS, initialBypassViewsUpdates)
      settings.showBlueprintCallstack = initialShowBlueprintCallstack
      settings.showUnrealFrames = initialShowUnrealFrames
    }
  }

  /**
   * Sets a toggle of the frames popup through its action, as a click does. The action must be visible: it is hidden when the
   * registry key is off or the host is not Windows.
   */
  private fun setToggle(actionId: String, selected: Boolean) {
    val (action, event) = createToggleEvent(actionId)
    event.presentation.isEnabledAndVisible.shouldBeTrue("Action $actionId is not available")
    action.setSelected(event, selected)
    action.isSelected(event).shouldBe(selected, "Action $actionId did not change its state")
  }

  private fun createToggleEvent(actionId: String): Pair<ToggleAction, AnActionEvent> {
    val action = ActionManager.getInstance().getAction(actionId) as ToggleAction
    val context = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project)
    val event = AnActionEvent.createEvent(action, context, null, "", ActionUiKind.NONE, null)
    action.update(event)
    return action to event
  }

  private fun DebugTestExecutionContext.collectFrames(): List<XStackFrame> {
    val stack = session.suspendContext?.activeExecutionStack ?: error("activeExecutionStack is null")
    return XDebuggerTestHelper.collectFrames(stack, Duration.ofSeconds(30)) { semaphore, duration ->
      waiter(semaphore, duration, "collectFrames")
    }
  }

  private fun assertTopFrameIsBreakpointFunction(frames: List<XStackFrame>) {
    val topFrame = frames.firstOrNull() ?: error("The call stack is empty")
    (topFrame !is UnrealFrameBase && topFrame.text().contains(BREAKPOINT_FUNCTION_NAME))
      .shouldBeTrue("The top frame is not $BREAKPOINT_FUNCTION_NAME:\n${frames.dump()}")
  }

  private fun assertBlueprintFramePresent(frames: List<XStackFrame>) {
    frames.none { it is StubBlueprintFrame }.shouldBeTrue("The call stack has a stub Blueprint frame:\n${frames.dump()}")
    val blueprintFrames = frames.filterIsInstance<BlueprintFrame>()
    blueprintFrames.size.shouldBe(1, "Expected one Blueprint frame:\n${frames.dump()}")
    blueprintFrames.single().text().contains(BLUEPRINT_FUNCTION_NAME)
      .shouldBeTrue("The Blueprint frame does not name $BLUEPRINT_FUNCTION_NAME:\n${frames.dump()}")
    (frames.indexOf(blueprintFrames.single()) > 0)
      .shouldBeTrue("The Blueprint frame is the top frame:\n${frames.dump()}")
  }

  private fun List<XStackFrame>.dump(): String = joinToString("\n") { "  [${it.javaClass.simpleName}] ${it.text()}" }

  private companion object {
    const val SHOW_BLUEPRINT_FUNCTIONS_ACTION_ID = "UnrealLink.Debugger.Rider.Unreal.Frames.ShowBlueprintFunctions"
    const val SHOW_UNREAL_FRAMES_ACTION_ID = "UnrealLink.Debugger.Rider.Unreal.Frames.HideUnrealFrames"
    const val TEST_PLUGIN_NAME = "BlueprintCallstackTestPlugin"
    const val BREAKPOINT_FUNCTION_NAME = "CalledFromBlueprint"
    const val BLUEPRINT_FUNCTION_NAME = "CallNativeFunction"
  }
}
