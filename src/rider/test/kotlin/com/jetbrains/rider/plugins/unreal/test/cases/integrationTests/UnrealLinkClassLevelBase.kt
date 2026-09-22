package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.junit5.unreal.UnrealClassLevelProject
import com.jetbrains.rider.test.scriptingApi.collectAndPublishDumpsToArtifacts
import com.jetbrains.rider.test.scriptingApi.waitPumping
import org.junit.jupiter.api.AfterEach
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * RiderLink install/uninstall shared by every [UnrealClassLevelProject]-rooted UnrealLink test.
 *
 * [UnrealLinkBase] is the same thing for [UnrealTestLevelProject][com.jetbrains.rider.test.junit5.unreal.UnrealTestLevelProject]-rooted
 * tests. The two roots share no fitting common ancestor for this, hence a separate base here rather
 * than reusing [UnrealLinkBase].
 */
abstract class UnrealLinkClassLevelBase : UnrealClassLevelProject() {

  /**
   * Undoes [installRiderLink] before [UnrealClassLevelProject.afterEachUnrealClassLevel] (the
   * superclass `@AfterEach`) restores the rest of the project — JUnit5 runs a subclass's own
   * `@AfterEach` before an inherited one, so this always goes first. The backend's `DeletePlugin`
   * handler cleans up both the project-local install and the engine's own plugin folder, whichever one
   * the test just used, so the next combination starts from a plugin-free project either way.
   *
   * [cleanUpUnrealCaches] also has to be called explicitly here: [UnrealClassLevelProject] never calls
   * it (only [com.jetbrains.rider.test.junit5.unreal.UnrealTestLevelProject.testTeardown] does, for the
   * per-test lifecycle these classes no longer use).
   */
  @AfterEach
  fun deleteInstalledPlugin() {
    if (solutionApiFacade.hasProject) deleteRiderLink()
    cleanUpUnrealCaches()
  }

  /**
   * @param useExtract `false` (the default — the "Install" action) compiles RiderLink from source.
   * `true` (the "Extract" action) unpacks the prebuilt plugin package as-is, skipping the UAT build step.
   */
  protected fun installRiderLink(place: PluginInstallLocation, useExtract: Boolean = false, timeout: Duration = Duration.ofSeconds(240)) {
    logger.info("Installing RiderLink in ${place.name}" + if (useExtract) " (extract)" else "")
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
        InstallPluginDescription(place, ForceInstall.Yes, buildRequired = !useExtract, emptyList(), emptyList())
      )

      // Wait until installation *finishes* (success or failure), not just until it starts.
      // A failed build now satisfies this quickly instead of hanging until a downstream timeout.
      try {
        waitAndPump(timeout, { finished }, { "RiderLink installation did not finish" })
      }
      catch (e: Exception) {
        collectAndPublishDumpsToArtifacts()
        throw e
      }

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
