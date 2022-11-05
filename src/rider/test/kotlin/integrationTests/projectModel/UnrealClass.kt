package integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.TestProjectModelContext
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.*
import com.jetbrains.rider.test.scriptingApi.TemplateType.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject

@Epic("Project Model")
@Feature("New Unreal Class")
@TestEnvironment(
    platform = [PlatformType.WINDOWS],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class UnrealClass : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }

    @Mute("RIDER-77926", specificParameters = ["Sln5_1fromSource", "Uproject5_1fromSource"])
    @Test(dataProvider = "enginesAndOthers")
    fun newUClass(@Suppress("UNUSED_PARAMETER") caseName: String,
                  openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInTestSetup(openWith, engine)
        project = openProject(openWith)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)
        doTestDumpProjectsView {
            // I'm limited by the technology of my time, but someday it will be better here.
            profile.customPathsToMask["absolute_ue_root"] = unrealInfo.currentEnginePath!!.toString()
            profile.customRegexToMask["number of projects"] = Regex("\\d,\\d\\d\\d projects")
            // Replace any quantity of ..\ or ../ and everything after them up to the root of the engine
            profile.customRegexToMask["relative_path_ue_root"] =
                Regex("(\\.\\.[\\\\/])+.*${unrealInfo.currentEnginePath!!.name}")
            profile.customRegexToMask["relative_path/"] =
                Regex("(\\.\\.[\\\\/])+")

            dump("Init") {}
            dump("Add different unreal class templates to '$activeSolution'") {
                val path = mutableListOf("EmptyUProject").apply {
                    if (openWith == EngineInfo.UnrealOpenType.Sln) add("Games")
                    add("EmptyUProject")
                    add("Source")
                    add("EmptyUProject")
                }.toTypedArray()
                for (template in unrealTemplates) {
                    val className = template.type.split(' ').joinToString("")
                    { word -> word.replaceFirstChar { it.uppercase() } }
                    logger.info("Adding ${template.type}")
                    addNewItem(project, path, template, className)
                }
            }
        }
    }

    @Mute(specificParameters = ["Sln5_1fromSource", "Uproject5_1fromSource"])
    @Test(dataProvider = "enginesAndOthers")
    fun moveUClass(@Suppress("UNUSED_PARAMETER") caseName: String,
                   openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInTestSetup(openWith, engine)
        project = openProject(openWith)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)

        doTestDumpProjectsView {
            profile.customPathsToMask["absolute_ue_root"] = unrealInfo.currentEnginePath!!.toString()
            profile.customRegexToMask["number of projects"] = Regex("\\d,\\d\\d\\d projects")
            profile.customRegexToMask["relative_path_ue_root"] = Regex("(\\.\\.[\\\\/])+.*${unrealInfo.currentEnginePath!!.name}")
            profile.customRegexToMask["relative_path/"] = Regex("(\\.\\.[\\\\/])+")

            val sourcePath = mutableListOf("EmptyUProject").apply {
                if (openWith == EngineInfo.UnrealOpenType.Sln) add("Games")
                add("EmptyUProject")
                add("Source")
                add("EmptyUProject")
            }.toTypedArray()

            addNewItem(project, sourcePath, UNREAL_ACTOR, "SomeActor")
            addNewFolder(project, sourcePath, "TestMove")

            dump("Init") {}
            dump("Moving .h and .cpp") {
                val moveToPath = sourcePath + "TestMove"
                moveItem(project, sourcePath + "SomeActor.h", moveToPath)
                moveItem(project, sourcePath + "SomeActor.cpp", moveToPath)
            }
        }
    }

    @Mute(specificParameters = ["Sln5_1fromSource", "Uproject5_1fromSource"])
    @Test(dataProvider = "enginesAndOthers")
    fun deleteUClass(@Suppress("UNUSED_PARAMETER") caseName: String,
                     openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInTestSetup(openWith, engine)
        project = openProject(openWith)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)

        doTestDumpProjectsView{
            profile.customPathsToMask["absolute_ue_root"] = unrealInfo.currentEnginePath!!.toString()
            profile.customRegexToMask["number of projects"] = Regex("\\d,\\d\\d\\d projects")
            profile.customRegexToMask["relative_path_ue_root"] = Regex("(\\.\\.[\\\\/])+.*${unrealInfo.currentEnginePath!!.name}")
            profile.customRegexToMask["relative_path/"] = Regex("(\\.\\.[\\\\/])+")

            val sourcePath = mutableListOf("EmptyUProject").apply {
                if (openWith == EngineInfo.UnrealOpenType.Sln) add("Games")
                add("EmptyUProject")
                add("Source")
                add("EmptyUProject")
            }.toTypedArray()

            addNewItem(project, sourcePath, UNREAL_ACTOR, "SomeActor")

            dump("Init Remove UClass test") {}
            dump("Deleting .h and .cpp") {
                deleteElement(project, sourcePath + "SomeActor.h")
                deleteElement(project, sourcePath + "SomeActor.cpp")
            }
        }
    }

    private val unrealTemplates: Array<TemplateType> =
        arrayOf(UNREAL_SIMPLE_TEST, UNREAL_COMPLEX_TEST, UNREAL_UOBJECT, UNREAL_ACTOR, UNREAL_ACTOR_COMPONENT,
            UNREAL_CHARACTER, UNREAL_EMPTY, UNREAL_INTERFACE, UNREAL_PAWN, UNREAL_SLATE_WIDGET,
            UNREAL_SLATE_WIDGET_STYLE, UNREAL_SOUND_EFFECT_SOURCE, UNREAL_SOUND_EFFECT_SUBMIX, UNREAL_SYNTH_COMPONENT)

    // TODO: delete after some refactoring at ScriptingApi.ProjectModel.kt
    private fun TestProjectModelContext.dump(
        caption: String,
        checkSlnFile: Boolean = false,
        checkIndex: Boolean = false,
        action: () -> Unit
    ) {
        dump(caption, project, activeSolutionDirectory.resolve("Source"), checkSlnFile, checkIndex, action)
    }

    // TODO: delete after some refactoring at ScriptingApi.ProjectModel.kt
    private fun doTestDumpProjectsView(action: TestProjectModelContext.() -> Unit) {
        testProjectModel(testGoldFile, project, action)
    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
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
                result.add(arrayOf(uniqueDataString("$type", engine), type, engine))
            }
        }
        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }
}
