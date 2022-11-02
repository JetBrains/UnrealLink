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
import com.jetbrains.rider.test.scriptingApi.*
import com.jetbrains.rider.test.scriptingApi.TemplateType.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject

@Epic("Project Model")
@Feature("New Unreal Class")
@TestEnvironment(
    platform = [PlatformType.WINDOWS_X64],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class UnrealClass : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
    }

    @Mute("RIDER-77926", specificParameters = ["Sln5_1fromSource", "Uproject5_1fromSource"])
    @Test(dataProvider = "AllEngines_AllPModels")
    fun newUClass(@Suppress("UNUSED_PARAMETER") caseName: String,
                  openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        testProjectModel(testGoldFile, project) {
            profile.customPathsToMask = unrealPathsToMask
            profile.customRegexToMask = unrealRegexToMask

            dump("Init") {}
            dump("Add different unreal class templates to '$activeSolution'") {
                val path = calculateRootPathInSolutionExplorer(activeSolution, openWith) + "Source" + "EmptyUProject"
                for (template in unrealTemplates) {
                    val className = template.type.split(' ').joinToString("")
                    { word -> word.replaceFirstChar { it.uppercase() } }
                    logger.info("Adding ${template.type}")
                    addNewItem(project, path, template, className)
                }
            }
        }
    }

    @Mute("RIDER-77926", specificParameters = ["Sln5_1fromSource", "Uproject5_1fromSource"])
    @Test(dataProvider = "AllEngines_AllPModels")
    fun moveUClass(@Suppress("UNUSED_PARAMETER") caseName: String,
                   openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        testProjectModel(testGoldFile, project) {
            profile.customPathsToMask = unrealPathsToMask
            profile.customRegexToMask = unrealRegexToMask

            val sourcePath = calculateRootPathInSolutionExplorer(activeSolution, openWith) + "Source"

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

    @Mute("RIDER-77926", specificParameters = ["Sln5_1fromSource", "Uproject5_1fromSource"])
    @Test(dataProvider = "AllEngines_AllPModels")
    fun deleteUClass(@Suppress("UNUSED_PARAMETER") caseName: String,
                     openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        testProjectModel(testGoldFile, project) {
            profile.customPathsToMask = unrealPathsToMask
            profile.customRegexToMask = unrealRegexToMask

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
}