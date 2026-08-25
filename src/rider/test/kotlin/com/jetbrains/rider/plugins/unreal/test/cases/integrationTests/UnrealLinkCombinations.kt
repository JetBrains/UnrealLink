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
 * Extends the standard `@UnrealCombinations` matrix with an additional [PluginInstallLocation]
 * dimension — the JUnit5 replacement for the TestNG `unrealLinkCombinations` data provider.
 *
 * Test method signature: `fun xxx(env: UnrealEnvironment, location: PluginInstallLocation)`.
 * Engine and openMode are applied by the standard Unreal method-level lifecycle; only the location
 * is specific to these tests.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TestTemplate
@ExtendWith(UnrealLinkCombinationProvider::class)
annotation class UnrealLinkCombinations

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

    val contexts: List<TestTemplateInvocationContext> = combinations.flatMap { (engine, openMode) ->
      locations.map { location ->
        UnrealMethodInvocationContext(
          env = UnrealEnvironment(engine, openMode),
          extraExtensions = listOf(PluginInstallLocationResolver(location)),
          displayNameSuffix = ", $location",
        )
      }
    }

    frameworkLogger.info("unrealLinkCombinations for ${method.name}: " +
                         "combinations=${combinations.size}, locations=${locations.size}, total=${contexts.size}")

    return contexts.stream()
  }
}

private class PluginInstallLocationResolver(private val location: PluginInstallLocation) : ParameterResolver {
  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type == PluginInstallLocation::class.java

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any = location
}
