package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.intellij.testFramework.utils.io.deleteRecursively
import com.jetbrains.rdclient.util.idea.waitAndPump
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
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
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
class UnrealLinkInstallation : UnrealLinkClassLevelBase() {
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
   *
   * `BuildRules` is excluded from the restore: `Intermediate/Build/BuildRules/<Project>ModuleRules.dll`
   * is Unreal Build Tool's own compiled rules assembly, not something [installRiderLink] or
   * [buildStartupProject] writes as a side effect this restore needs to undo, and this run's own
   * `ul` case — [withRunProgram]'s launched Unreal Editor process, or a UBT/MSBuild child from
   * [buildStartupProject] — can still hold it open for a moment after that case's body returns, before
   * this `@AfterEach` runs. Comparing or overwriting it here raced a transient Windows
   * `FileSystemException` ("The process cannot access the file because it is being used by another
   * process"); excluding it removes the race instead of retrying around it. The retry loop stays as a
   * defense against any other transient hold elsewhere under `Intermediate`.
   */
  override fun restoreInitialProjectState() {
    super.restoreInitialProjectState()
    val liveIntermediate = project.solutionDirectoryPath.resolve("Intermediate")
    val backupIntermediate = testWorkDirectory.resolve("${activeSolution}_backup").resolve("Intermediate")
    if (!backupIntermediate.exists()) {
      liveIntermediate.deleteRecursively()
      return
    }
    val profile = BackupRestoreProfile().apply { ignoreDirs.add("BuildRules") }
    var attempt = 1
    while (true) {
      try {
        restoreProject(liveIntermediate, backupIntermediate, profile)
        return
      }
      catch (e: java.io.IOException) {
        if (attempt >= 5) throw e
        logger.warn("restoreProject(Intermediate) attempt $attempt failed, retrying: ${e.message}")
        waitPumping(Duration.ofMillis(500))
        attempt++
      }
    }
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
}
