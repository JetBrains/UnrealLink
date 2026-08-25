@file:Suppress("JUnitTestCaseWithNoTests")

package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.junit5.unreal.UnrealCombinations
import com.jetbrains.rider.test.junit5.unreal.UnrealTestLevelProject
import org.junit.jupiter.api.AfterEach
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedDeque

open class UnrealLinkBase: UnrealTestLevelProject() {
  /**
   * Runs while the solution is still open — [deleteRiderLink] talks to the backend over the protocol.
   * The base cache cleanup that happens after the solution is closed lives in
   * `UnrealTestLevelProject.testTeardown`, the superclass `@AfterEach`, which JUnit5 runs after this one.
   *
   * The `hasProject` guard replaces TestNG's "skip `@AfterMethod` when a config method failed":
   * JUnit5 runs `@AfterEach` even when the solution never opened, and without the guard that would
   * surface as an NPE here instead of the real setup failure.
   */
  @AfterEach
  override fun unrealCleanup() {
    if (solutionApiFacade.hasProject) {
      deleteRiderLink()
    }
    super.unrealCleanup()
  }

  protected fun installRiderLink(place: PluginInstallLocation, timeout: Duration = Duration.ofSeconds(240)) {
    logger.info("Installing RiderLink in ${place.name}")
    Lifetime.using { lifetime ->
      var finished = false
      var installSucceeded = false
      // Compiler/UAT output arrives as ContentType.Normal (stdout), so we keep the tail of
      // every message regardless of type to surface the real failure reason on a fast-fail.
      val messageTail = ConcurrentLinkedDeque<String>()

      project.solution.rdRiderModel.riderLinkInstallMessage.advise(lifetime) { msg ->
        messageTail.addLast("[${msg.type}] ${msg.text}")
        while (messageTail.size > 50) messageTail.pollFirst()
      }
      project.solution.rdRiderModel.installPluginFinished.advise(lifetime) { success ->
        installSucceeded = success
        finished = true
      }

      project.solution.rdRiderModel.installEditorPlugin.fire(
        InstallPluginDescription(place, ForceInstall.Yes, true, emptyList(), emptyList())
      )

      // Wait until installation *finishes* (success or failure), not just until it starts.
      // A failed build now satisfies this quickly instead of hanging until a downstream timeout.
      waitAndPump(timeout, { finished }, { "RiderLink installation did not finish" })

      if (!installSucceeded) {
        error("RiderLink installation failed in ${place.name}. Last install output:\n" +
              messageTail.joinToString("\n"))
      }
    }
  }

  protected fun deleteRiderLink() {
    project.solution.rdRiderModel.deletePlugin.fire()
    waitPumping(Duration.ofSeconds(5))
  }
}
