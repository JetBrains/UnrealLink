package integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.TestProjectModelContext
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.*
import com.jetbrains.rider.test.scriptingApi.TemplateType.*
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.time.Duration
import kotlin.collections.ArrayList


@TestEnvironment(
    platform = [PlatformType.WINDOWS],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class UnrealClass : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
        openSolutionParams.waitForCaches = true
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(150)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(150)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)
    }

    // TODO: delete after some refactoring at ScriptingApi.ProjectModel.kt
    private fun TestProjectModelContext.dump(
        caption: String,
        checkSlnFile: Boolean = false,
        checkIndex: Boolean = false,
        action: () -> Unit
    ) {
        dump(caption, project, activeSolutionDirectory, checkSlnFile, checkIndex, action)
    }

    // TODO: delete after some refactoring at ScriptingApi.ProjectModel.kt
    private fun doTestDumpProjectsView(action: TestProjectModelContext.() -> Unit) {
        testProjectModel(testGoldFile, project, action)
    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        val unrealTemplates: Array<TemplateType> =
            arrayOf(UNREAL_SIMPLE_TEST, UNREAL_COMPLEX_TEST, UNREAL_UOBJECT, UNREAL_ACTOR, UNREAL_ACTOR_COMPONENT,
                    UNREAL_CHARACTER, UNREAL_EMPTY, UNREAL_INTERFACE, UNREAL_PAWN, UNREAL_SLATE_WIDGET,
                    UNREAL_SLATE_WIDGET_STYLE, UNREAL_SOUND_EFFECT_SOURCE, UNREAL_SOUND_EFFECT_SUBMIX, UNREAL_SYNTH_COMPONENT)
        val guidRegex = "^[{]?[\\da-fA-F]{8}-([\\da-fA-F]{4}-){3}[\\da-fA-F]{12}[}]?$".toRegex()

        // Little hack for generate unique name in com.jetbrains.rider.test.TestCaseRunner#extractTestName
        //  based on file template type, UnrealOpenType, engine version and what engine uses - EGS/Source.
        // Unique name need for gold file/dir name.
        val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, engine: UnrealEngine ->
            // If we use engine from source, it's ID is GUID, so we replace it by 'normal' id plus ".fromSouce" string
            // else just replace dots in engine version, 'cause of part after last dot will be parsed as file type.
            if (engine.id.matches(guidRegex)) "$baseString${engine.version.major}_${engine.version.minor}fromSource"
            else "$baseString${engine.id.replace('.', '_')}"
        }
        unrealInfo.testingEngines.forEach { engine ->
            arrayOf(EngineInfo.UnrealOpenType.Uproject, EngineInfo.UnrealOpenType.Sln).forEach { type ->
                unrealTemplates.forEach { template ->
                    result.add(
                        arrayOf(
                            uniqueDataString("${template.type.replace(" ", "")}$type", engine),
                            template,
                            type,
                            engine
                        )
                    )
                }
            }
        }
        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }

    // Mandatory function before opening an unreal project
    private fun unrealInTestSetup(openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInfo.currentEngine = engine

        println("Test starting with $engine, opening by $openWith.")

        replaceUnrealEngineVersionInUproject(uprojectFile, unrealInfo.currentEngine!!)

        if (openWith == EngineInfo.UnrealOpenType.Sln) {
            generateSolutionFromUProject(uprojectFile)
            openSolutionParams.minimalCountProjectsMustBeLoaded = null
        } else {
            openSolutionParams.minimalCountProjectsMustBeLoaded = 1400 // TODO: replace the magic number with something normal
        }
    }

    @Test(dataProvider = "enginesAndOthers")
    fun newUClass(caseName: String, template: TemplateType, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInTestSetup(openWith, engine)
        project = openProject(openWith)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)
        doTestDumpProjectsView {
            profile.customPathsToMask["absolute_ue_root"] = unrealInfo.currentEngine!!.path
            profile.customRegexToMask["relative_path/"] = Regex("(\\.\\.[\\\\/])+") // Any quantity ..\ or ../
            dump("Init") {}
            dump("Add ${template.type} to 'EmptyUProject'") {
                val path = mutableListOf("EmptyUProject").apply {
                    if (openWith == EngineInfo.UnrealOpenType.Sln) add("Games")
                    add("EmptyUProject")
                    add("Source")
                    add("EmptyUProject")
                }.toTypedArray()
                val className = template.type.split(' ').joinToString("")
                    { word -> word.replaceFirstChar { it.uppercase() } }
                addNewItem(project, path, template, className)
            }
        }
    }

    // Special test template for manual launch with specific parameters.
    // Just do "enable = true" and set openWith, engine and template variables.
    @Test(enabled = false)
    fun newUnrealClass_single() {
        val openWith = EngineInfo.UnrealOpenType.Uproject
        val engine = unrealInfo.testingEngines.find { it.id == "5.0" && it.isInstalledBuild }!!
        val template = UNREAL_ACTOR_COMPONENT

        val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, eng: UnrealEngine ->
            "$baseString${eng.id.replace('.', '_')}"
        }
        val caseName = uniqueDataString("${template.type.replace(" ", "")}$openWith", engine)
        newUClass(caseName, template, openWith, engine)
    }
}