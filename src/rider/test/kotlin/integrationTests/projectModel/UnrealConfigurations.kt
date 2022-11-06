package integrationTests.projectModel

import com.intellij.openapi.project.Project
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rider.projectView.SolutionConfigurationManager
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.executeWithGold
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.io.PrintStream

@Epic("Project Model")
@Feature("Solution Configurations")
@TestEnvironment(
    platform = [PlatformType.WINDOWS_X64],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class UnrealConfigurations : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }

    @Mute("Can't open backend solution in 400s", specificParameters = ["Uproject5_1fromSource"])
    @Test(dataProvider = "AllEngines_AllPModels")
    fun solutionConfigurationsLoaded(
        @Suppress("UNUSED_PARAMETER") caseName: String,
        openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine
    ) {
        executeWithGold(testGoldFile) { printStream ->
            doTestDumpSolutionConfigurations(project, printStream)
        }
    }

    private fun doTestDumpSolutionConfigurations(project: Project, printStream: PrintStream) {
        val slnConfManager = SolutionConfigurationManager.getInstance(project)
        val slnConfAndPlatforms = slnConfManager.solutionConfigurationsAndPlatforms

        printStream.println("Count: ${slnConfAndPlatforms.size}")
        for (cfg in slnConfAndPlatforms)
            printStream.println(cfg)
    }
}