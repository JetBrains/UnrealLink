package integrationTests.blueprint

import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.protocol.getComponent
import com.jetbrains.rider.codeLens.settings.CodeLensSettingsHost
import com.jetbrains.rider.protocol.protocolHost
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.scriptingApi.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject

@Epic("Blueprint")
@Feature("Code Vision")
@TestEnvironment(platform = [PlatformType.WINDOWS_X64], toolset = ToolsetVersion.TOOLSET_16_CPP, coreVersion = CoreVersion.DEFAULT)
class CodeVision : UnrealTestProject() {

    init {
        projectDirectoryName = "BlueprintCodeVision"
        disableEngineIndexing = false
    }

    @Test(dataProvider = "AllEngines_slnOnly")
    fun allLensProviders(
        @Suppress("UNUSED_PARAMETER") caseName: String,
        openWith: EngineInfo.UnrealOpenType,
        engine: UnrealEngine
    ) {
        waitForLensInfos(project)
        enableAllLensProviders()
        logAllLenseProviders()
        waitForAllAnalysisFinished(project)

        val editorActor = withOpenedEditor(
            project,
            activeSolutionDirectory.resolve("Source")
                .resolve(activeSolution).resolve("MyActor.h").absolutePath
        ) {
            installGlobalDaemonWatcher()
            waitForLenses()

            executeWithGold(testGoldFile, "_actor") {
                it.print(dumpLenses())
            }
        }
        closeEditor(editorActor)

        val editorAnimInst = withOpenedEditor(
            project,
            activeSolutionDirectory.resolve("Source")
                .resolve(activeSolution).resolve("MyAnimInstance.h").absolutePath
        ) {
            installGlobalDaemonWatcher()
            waitForLenses()

            executeWithGold(testGoldFile, "_animInst") {
                it.print(dumpLenses())
            }
        }
        closeEditor(editorAnimInst)
    }

    private fun logAllLenseProviders() {
        val allProviderIds = project.protocolHost.getComponent<CodeLensSettingsHost>().settingsModel.providers.keys +
                CodeVisionProvider.providersExtensionPoint.extensions.map { it.id }
        logger.info("Found lenses providers: $allProviderIds")
    }
}
