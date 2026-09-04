package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.junit5.unreal.UnrealMethodInvocationContext
import com.jetbrains.rider.test.unreal.UnrealEnvironment
import com.jetbrains.rider.test.unreal.UnrealTestCombinations
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.util.stream.Stream

/**
 * Extends the standard `@UnrealCombinations` matrix with an additional [PluginInstallLocation] and
 * [PluginInstallMethod] dimension — the JUnit5 replacement for the TestNG `unrealLinkCombinations`
 * data provider.
 *
 * Test method signature: `fun xxx(env: UnrealEnvironment, location: PluginInstallLocation, installMethod: PluginInstallMethod)`.
 * Engine and openMode are applied by the standard Unreal method-level lifecycle; only the location
 * and the install method are specific to these tests.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TestTemplate
@ExtendWith(UnrealLinkCombinationProvider::class)
annotation class UnrealLinkCombinations

/**
 * How RiderLink is placed at the target [PluginInstallLocation] — the JUnit5-side counterpart of
 * `installRiderLink`'s `useExtract` parameter and the frontend's "Install"/"Extract" actions.
 *
 * [Build] (the default) compiles RiderLink from source with UAT. [Extract] unpacks the prebuilt
 * plugin package as-is and skips the build step.
 */
enum class PluginInstallMethod(val useExtract: Boolean) {
  Build(false),
  Extract(true),
}

class UnrealLinkCombinationProvider : TestTemplateInvocationContextProvider {
  override fun supportsTestTemplate(context: ExtensionContext): Boolean {
    val method = context.testMethod.orElse(null) ?: return false
    return method.isAnnotationPresent(UnrealLinkCombinations::class.java)
  }

  /** Same rationale as `UnrealMethodCombinationProvider`: an empty matrix is a skip, not an error. */
  override fun mayReturnZeroTestTemplateInvocationContexts(context: ExtensionContext): Boolean = true

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    val method = context.requiredTestMethod
    val combinations = UnrealTestCombinations.combinations(method)
    val locations = listOf(PluginInstallLocation.Game, PluginInstallLocation.Engine)
    val installMethods = listOf(PluginInstallMethod.Build, PluginInstallMethod.Extract)

    val contexts: List<TestTemplateInvocationContext> = combinations.flatMap { (engine, openMode) ->
      locations.flatMap { location ->
        installMethods
          // Extracting into the Engine plugins folder drops un-compiled sources there; only a
          // from-source engine can rebuild its own Editor to pick them up. An installed (EGS)
          // engine can't, which is why the frontend hides "Extract to Engine" for it too.
          .filter { location != PluginInstallLocation.Engine || it == PluginInstallMethod.Build || !engine.isInstalledBuild }
          .map { installMethod ->
            // No ", " here: RiderJUnit5TeamCityListener.getTestName reduces the reported TC name to
            // `displayName.split(", ")[0]` — a ", "-separated suffix is silently dropped, and every
            // location/installMethod invocation for one (engine, openMode) collapses onto the same
            // TC test, so later runs overwrite earlier ones instead of reporting separately.
            val suffix = "$location" + (if (installMethod == PluginInstallMethod.Extract) "Extract" else "")
            UnrealMethodInvocationContext(
              env = UnrealEnvironment(engine, openMode),
              extraExtensions = listOf(PluginInstallLocationResolver(location), PluginInstallMethodResolver(installMethod)),
              displayNameSuffix = suffix,
            )
          }
      }
    }

    frameworkLogger.info("unrealLinkCombinations for ${method.name}: " +
                         "combinations=${combinations.size}, locations=${locations.size}, " +
                         "installMethods=${installMethods.size}, total=${contexts.size}")

    return contexts.stream()
  }
}

private class PluginInstallLocationResolver(private val location: PluginInstallLocation) : ParameterResolver {
  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type == PluginInstallLocation::class.java

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any = location
}

private class PluginInstallMethodResolver(private val installMethod: PluginInstallMethod) : ParameterResolver {
  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type == PluginInstallMethod::class.java

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any = installMethod
}
