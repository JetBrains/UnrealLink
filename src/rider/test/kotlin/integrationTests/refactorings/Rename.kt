package integrationTests.refactorings

import com.intellij.openapi.editor.impl.EditorImpl
import com.jetbrains.ide.model.uiautomation.BeCheckbox
import com.jetbrains.ide.model.uiautomation.BeTextBox
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ui.bedsl.extensions.getBeControlById
import com.jetbrains.rd.ui.bedsl.extensions.tryGetBeControlById
import com.jetbrains.rider.actions.RiderActions
import com.jetbrains.rider.model.refactorings.BeRefactoringsPage
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.*
import com.jetbrains.rider.test.scriptingApi.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.io.File
import java.time.Duration

// TODO Split for multiple tests
// TODO Use common data provider
// TODO replace dumping functions with generic ones from TestFramework
@Epic("Refactorings")
@Feature("Rename")
@TestEnvironment(
    platform = [PlatformType.WINDOWS], toolset = ToolsetVersion.TOOLSET_16_CPP, coreVersion = CoreVersion.DEFAULT
)
class Rename : UnrealTestProject() {
    init {
        projectDirectoryName = "TestProjectAndPlugin"
        openSolutionParams.waitForCaches = true
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(150)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(400)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)
    }

    /**
     * A simple Unreal rename refactoring test (really it must be two tests, but now it's kinda difficult to do this).
     * When we rename Unreal symbol we use Core Redirect functionality (simplified this is a text pointer from
     * the old name to the new one in the .ini file).
     *
     * This test renames class and property twice to make sure that rename works normally and all needed redirects appear.
     * [openWith] and [engine] come from data provider based on data from [EngineInfo.testingEngines]
     */
    @Test(dataProvider = "enginesAndOthers")
    fun rename(@Suppress("UNUSED_PARAMETER")
               caseName: String,
               openWith: EngineInfo.UnrealOpenType,
               engine: UnrealEngine
    ) {
        unrealInTestSetup(openWith, engine)
        project = openProject(openWith)

        val projectFile = "$activeSolutionDirectory/Source/$projectDirectoryName/MyActor.h"
        val pluginFile = "$activeSolutionDirectory/Plugins/TestPlugin/Source/TestPlugin/Public/MyPluginActor.h"

        val projectDumpedItems = listOf(
            File(activeSolutionDirectory, "Source"),
            File(activeSolutionDirectory, "Config")
        )
        val pluginDumpedItems = listOf(
            File(activeSolutionDirectory, "Plugins").resolve("TestPlugin").resolve("Config"),
            File(activeSolutionDirectory, "Plugins").resolve("TestPlugin").resolve("Source")
        )

        val dumpProfile = TestProjectModelDumpFilesProfile().apply {
            extensions.clear()
            extensions.addAll(arrayOf("cpp", "h", "ini")) // only these extensions would be dumped
        }

        withDumpEachStep(File(testCaseGoldDirectory, "${testMethod.name}_project"),
            projectDumpedItems,
            dumpProfile,
            Pair("Rename class in project") {
                withOpenedEditor(project, projectFile) {
                    renameUnrealSymbol("AMyActor", "AMyActorRename",
                        renameFile = false, editor = this)
                }
            },
            Pair("Rename property in project") {
                withOpenedEditor(project, projectFile) {
                    renameUnrealSymbol("bMyProperty", "bMyPropertyRename",
                        renameFile = false, editor = this)
                }
            },
            Pair("Second rename class in project") {
                withOpenedEditor(project, projectFile) {
                    renameUnrealSymbol("AMyActorRename", "AMyActorSecondRename",
                        renameFile = false, editor = this)
                }
            },
            Pair("Second rename property in project") {
                withOpenedEditor(project, projectFile) {
                    renameUnrealSymbol(
                        "bMyPropertyRename", "bMyPropertySecondRename",
                        renameFile = false, editor = this
                    )
                }
            }
        )

        withDumpEachStep(File(testCaseGoldDirectory, "${testMethod.name}_plugin"),
            pluginDumpedItems,
            dumpProfile,
            Pair("Rename class in plugin") {
                withOpenedEditor(project, pluginFile) {
                    renameUnrealSymbol("AMyPluginActor", "AMyPluginActorRename",
                        renameFile = false, editor = this)
                }
            },
            Pair("Rename property in plugin") {
                withOpenedEditor(project, pluginFile) {
                    renameUnrealSymbol("bMyProperty", "bMyPropertyRename",
                        renameFile = false, editor = this)
                }
            },
            Pair("Second rename class in plugin") {
                withOpenedEditor(project, pluginFile) {
                    renameUnrealSymbol(
                        "AMyPluginActorRename", "AMyPluginActorSecondRename",
                        renameFile = false, editor = this
                    )
                }
            },
            Pair("Second rename property in plugin") {
                withOpenedEditor(project, pluginFile) {
                    renameUnrealSymbol(
                        "bMyPropertyRename", "bMyPropertySecondRename",
                        renameFile = false, editor = this
                    )
                }
            }
        )
    }

    /**
     * Performs some [actions] and writes the action title (Pair.first from each action) and content of [dumpItems] (can be files or dirs)
     * according to [dumpProfile] to [testGoldFile] after each action.
     */
    private fun withDumpEachStep(
        testGoldFile: File,
        dumpItems: List<File>,
        dumpProfile: TestProjectModelDumpFilesProfile = TestProjectModelDumpFilesProfile(),
        vararg actions: Pair<String, () -> Unit>
    ) {
        executeWithGold(testGoldFile) { printStream ->
            val resultDump = StringBuilder()

            for (action in actions) {
                resultDump.append(doActionAndDump(action.first, dumpItems, dumpProfile, action.second))
            }

            printStream.append(resultDump)
        }
    }

    /**
     * Creates an empty [StringBuilder], do some [action] and write [dumpCaption] and content of [dumpItems] (can be files or dirs)
     * according to [dumpProfile] to this builder.
     * @return [StringBuilder] filled with the resulting dump.
     */
    private fun doActionAndDump(
        dumpCaption: String,
        dumpItems: List<File>,
        dumpProfile: TestProjectModelDumpFilesProfile = TestProjectModelDumpFilesProfile(),
        action: () -> Unit
    ): StringBuilder {
        val sb = StringBuilder()
        sb.appendLine(dumpCaption)
        sb.appendLine()
        action()
        for (item in dumpItems) {
            assert(item.exists())
            if (item.isDirectory) {
                dumpFiles(sb, item, false, dumpProfile)
            } else if (item.isFile) {
                sb.appendLine("[${item.name}]")
                sb.appendLine(item.readText())
                sb.appendLine()
            }
        }
        return sb
    }

    /**
     * Renames Unreal Engine symbol from [oldSymbolName] to [newSymbolName].
     * Can rename files along with the symbol, for example when renaming class (optional) by [renameFile].
     * Can add Core Redirects (optional) by [addCoreRedirect].
     * Some [editor] must be set to work, for example by [withOpenedEditor].
     */
    private fun renameUnrealSymbol(
        oldSymbolName: String,
        newSymbolName: String,
        renameFile: Boolean = true,
        addCoreRedirect: Boolean = true,
        editor: EditorImpl
    ) {
        logger.debug("Start renaming $oldSymbolName to $newSymbolName")
        editor.setCaretBeforeWord(oldSymbolName)
        var pageNumber = 0
        withPageWithClickedNext(project, function = {
            callActionAndHandlePopup(RiderActions.REFACTOR_THIS, editor.dataContext) {
                executeItem("Rename")
            }
        }, pageActions = {
            val page = this as BeRefactoringsPage
            val content = page.content
            when (pageNumber) {
                0 -> {
                    val nameCheckbox = content.getBeControlById<BeTextBox>("Rename.Name")
                    nameCheckbox.text.set(newSymbolName)

                    val renameFileCheckbox = content.tryGetBeControlById<BeCheckbox>("Rename.RenameFile")
                    renameFileCheckbox?.property?.set(renameFile) // TODO fix exceptions and set(true)
                }

                1 -> {
                    val addCoreRedirectCheckbox = content.getBeControlById<BeCheckbox>("ShouldCoreRedirect")
                    addCoreRedirectCheckbox.property.set(addCoreRedirect)
                }

                else -> {
                    Assert.fail("Unexpected page $page")
                }
            }
            pageNumber += 1
        })
        waitBackendAndWorkspaceModel(project)
        persistAllFilesOnDisk()
    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, engine: UnrealEngine ->
            "$baseString${engine.id.replace('.', '_')}"
        }

        for (openWith in arrayOf(EngineInfo.UnrealOpenType.Uproject, EngineInfo.UnrealOpenType.Sln)) {
            for (engine in unrealInfo.testingEngines.filter { it.isInstalledBuild }) {
                result.add(arrayOf(uniqueDataString("$openWith", engine), openWith, engine))
            }
        }

        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }
}