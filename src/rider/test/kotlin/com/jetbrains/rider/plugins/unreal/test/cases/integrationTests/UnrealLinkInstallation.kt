package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.intellij.testFramework.utils.io.deleteRecursively
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.solutionDirectoryPath
import com.jetbrains.rider.test.annotations.RiderTestTimeout
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.Subsystem
import com.jetbrains.rider.test.annotations.report.ChecklistItems
import com.jetbrains.rider.test.annotations.report.Feature
import com.jetbrains.rider.test.junit5.unreal.UnrealClassLevelProject
import com.jetbrains.rider.test.reporting.SubsystemConstants
import com.jetbrains.rider.test.scriptingApi.setUnrealConfigurationAndPlatform
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.scriptingApi.withRunProgram
import com.jetbrains.rider.test.shared.constants.TeamCityTags
import com.jetbrains.rider.test.suplementary.RiderTestSolution
import com.jetbrains.rider.test.unreal.UnrealConstants
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.io.path.exists

/**
 * How RiderLink is placed at the target [PluginInstallLocation] — the JUnit5-side counterpart of
 * [UnrealLinkInstallation.installRiderLink]'s `useExtract` parameter and the frontend's
 * "Install"/"Extract" actions.
 *
 * [Build] (the default) compiles RiderLink from source with UAT. [Extract] unpacks the prebuilt
 * plugin package as-is and skips the build step.
 */
enum class PluginInstallMethod(val useExtract: Boolean) {
  Build(false),
  Extract(true),
}

/**
 * One (location, installMethod) case, run against the solution already open for the current
 * (engine, openMode) class-template invocation — see [UnrealLinkInstallation].
 */
data class InstallCase(val location: PluginInstallLocation, val installMethod: PluginInstallMethod) {
  /**
   * No ", " here: `RiderJUnit5TeamCityListener.getTestName` builds the reported name from the enclosing
   * class-template invocation (`Uproject5_7`) plus this string, with no separator between them, to
   * match the legacy TestNG-style name (`ul("Uproject5_7Game")`). A ", " inside this string would still
   * be safe here — `getTestName` reads a `@ParameterizedTest(name = "{0}")` invocation's `displayName`,
   * which already carries only this one argument — but earlier `@TestTemplate`-based code depended on
   * a stricter no-comma rule, so this keeps the same convention.
   */
  override fun toString(): String = "$location" + (if (installMethod == PluginInstallMethod.Extract) "Extract" else "")
}

@Tag(TeamCityTags.GameDev.Unreal.Link.General)
@Tag(TeamCityTags.GameDev.Unreal.Link.Smoke)
@Subsystem(SubsystemConstants.UNREAL_LINK)
@Feature("Installation")
@Solution(RiderTestSolution.Unreal.EmptyUProject)
class UnrealLinkInstallation : UnrealClassLevelProject() {
  private val runProgramTimeout: Duration = Duration.ofMinutes(10)

  /**
   * `disableEnginePlugins` is set here rather than in a `@BeforeEach`: for a `@ClassTemplate` test,
   * [UnrealClassLevelProject.ensureUnrealSolutionOpen] — the superclass `@BeforeEach` that opens the
   * solution — always runs before this class's own `@BeforeEach` methods, so a `@BeforeEach` here would
   * set the flag one solution-open too late. [beforeOpenSolution] runs synchronously inside that same
   * open, before the `.uproject` file is generated, which is early enough.
   */
  override fun beforeOpenSolution() {
    super.beforeOpenSolution()
    unrealApiFacade.disableEnginePlugins = false
  }

  /**
   * The (location, installMethod) matrix. (engine, openMode) is the `@ClassTemplate` dimension and
   * stays governed by [com.jetbrains.rider.test.unreal.UnrealTestCombinations] — unaffected by this.
   *
   * `unrealEnv` is not available yet when this runs (`@MethodSource` is evaluated before `@BeforeAll`
   * for a `@ClassTemplate`), so the engine-dependent Engine+Extract restriction cannot be filtered out
   * here. It is skipped per case instead, inside [ul].
   */
  fun installCases(): Stream<Arguments> =
    listOf(PluginInstallLocation.Game, PluginInstallLocation.Engine)
      .flatMap { location -> listOf(PluginInstallMethod.Build, PluginInstallMethod.Extract).map { InstallCase(location, it) } }
      .generateDataProvider { Arguments.of(it.toString(), it) }

  /**
   * Undoes [installRiderLink] before [UnrealClassLevelProject.afterEachUnrealClassLevel] (the
   * superclass `@AfterEach`) restores the rest of the project — JUnit5 runs a subclass's own
   * `@AfterEach` before an inherited one, so this always goes first. The backend's `DeletePlugin`
   * handler cleans up both the project-local install and the engine's own plugin folder, whichever one
   * the case just used, so the next case starts from a plugin-free project either way.
   *
   * [cleanUpUnrealCaches] also has to be called explicitly here: [UnrealClassLevelProject] never calls
   * it (only [com.jetbrains.rider.test.junit5.unreal.UnrealTestLevelProject.testTeardown] does, for the
   * per-test lifecycle this class no longer uses). Every case here compiles or extracts a plugin into
   * the engine and launches it, which is exactly the kind of run [cleanUpUnrealCaches] guards — leaving
   * a stale `UnrealEngine/Intermediate` from one case's build/extract could affect the next case's, or
   * the next engine's.
   */
  @AfterEach
  fun deleteInstalledPlugin() {
    if (solutionApiFacade.hasProject) deleteRiderLink()
    cleanUpUnrealCaches()
  }

  /**
   * All 4 cases of one (engine, openMode) share the same on-disk work directory —
   * [UnrealClassLevelProject.testWorkDirectoryStorage] is keyed by `unrealEnv`, not by the individual
   * case, unlike the previous per-test design where every (location, installMethod) invocation got its
   * own, never-reused work directory. The default [restoreInitialProjectState] restores the project
   * from its post-open backup but deliberately **excludes** `Intermediate` from that restore, so without
   * this override a build's or extract's leftovers under `Intermediate` would still be on disk for the
   * next case — e.g. an Extract case's plugin binaries surviving into a following Build case, letting a
   * build silently pass with prebuilt objects it never produced itself.
   *
   * Restored from the same `${activeSolution}_backup` the default restore already uses, rather than
   * deleted outright: for `Sln` open mode, [RiderUnrealApiFacade.generateSln][com.jetbrains.rider.test.facades.unreal.RiderUnrealApiFacade.generateSln]
   * runs Unreal Build Tool's `-ProjectFiles` step once, during solution open, and writes into
   * `Intermediate/ProjectFiles` — a plain delete here would remove that for the 2nd–4th case of the
   * invocation, not just this case's own build/extract leftovers. [backupProject] is a raw recursive
   * copy with no `ignoreDirs` filtering, so that backup already holds the exact post-open `Intermediate`
   * (UBT project files for `Sln`, nothing for `Uproject` — [generateSln] is a no-op there) — restoring
   * from it undoes only what a case itself added, the same guarantee the rest of the project gets.
   */
  override fun restoreInitialProjectState() {
    super.restoreInitialProjectState()
    val liveIntermediate = project.solutionDirectoryPath.resolve("Intermediate")
    val backupIntermediate = testWorkDirectory.resolve("${activeSolution}_backup").resolve("Intermediate")
    if (backupIntermediate.exists())
      restoreProject(liveIntermediate, backupIntermediate, BackupRestoreProfile())
    else
      liveIntermediate.deleteRecursively()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("installCases")
  @RiderTestTimeout(10, TimeUnit.MINUTES)
  @ChecklistItems(["UnrealLink/Installation"])
  fun ul(@Suppress("UNUSED_PARAMETER") caseName: String, case: InstallCase) {
    // Extracting into the Engine plugins folder drops un-compiled sources there; only a from-source
    // engine can rebuild its own Editor to pick them up. An installed (EGS) engine can't, which is why
    // the frontend hides "Extract to Engine" for it too.
    Assumptions.assumeFalse(
      case.location == PluginInstallLocation.Engine
      && case.installMethod == PluginInstallMethod.Extract
      && unrealEnv.engine.isInstalledBuild,
      "Extract into the Engine plugins folder needs a from-source engine"
    )

    setUnrealConfigurationAndPlatform(project, UnrealConstants.UnrealConfigurations.DevelopmentEditor)

    installRiderLink(case.location, case.installMethod.useExtract)

    buildStartupProject()

    withRunProgram(project, configurationName = activeSolution) {
      waitAndPump(runProgramTimeout, { it.solution.rdRiderModel.isConnectedToUnrealEditor.value }, { "Not connected to UnrealEditor" })
      waitPumping(Duration.ofSeconds(15))
    }
  }

  /**
   * @param useExtract `false` (the default — the "Install" action) compiles RiderLink from source.
   * `true` (the "Extract" action) unpacks the prebuilt plugin package as-is, skipping the UAT build step.
   *
   * Duplicated from [UnrealLinkBase.installRiderLink] rather than shared: that base is
   * [com.jetbrains.rider.test.junit5.unreal.UnrealTestLevelProject]-rooted, this class is
   * [UnrealClassLevelProject]-rooted, and the two roots share no fitting common ancestor for this.
   */
  private fun installRiderLink(place: PluginInstallLocation, useExtract: Boolean = false, timeout: Duration = Duration.ofSeconds(240)) {
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
      waitAndPump(timeout, { finished }, { "RiderLink installation did not finish" })

      if (!installSucceeded) {
        error("RiderLink installation failed in ${place.name}. Last install output:\n" +
              messageTail.joinToString("\n"))
      }
    }
  }

  /** Duplicated from [UnrealLinkBase.deleteRiderLink] — see [installRiderLink] for why. */
  private fun deleteRiderLink() {
    project.solution.rdRiderModel.deletePlugin.fire()
    waitPumping(Duration.ofSeconds(5))
  }
}
