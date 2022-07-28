package integrationTests.renameRefactoring

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.ide.model.uiautomation.BeCheckbox
import com.jetbrains.ide.model.uiautomation.BeTextBox
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ui.bedsl.extensions.getBeControlById
import com.jetbrains.rd.ui.bedsl.extensions.tryGetBeControlById
import com.jetbrains.rider.actions.RiderActions
import com.jetbrains.rider.model.refactorings.BeRefactoringsPage
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.framework.waitBackendAndWorkspaceModel
import com.jetbrains.rider.test.scriptingApi.*
import org.apache.commons.io.FileUtils
import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.io.File
import java.io.PrintStream
import java.time.Duration

@TestEnvironment(
    platform = [PlatformType.WINDOWS], toolset = ToolsetVersion.TOOLSET_16_CPP, coreVersion = CoreVersion.DEFAULT
)
class Rename : UnrealTestProject() {
    init {
        projectDirectoryName = "TestProjectAndPlugin"
        openSolutionParams.waitForCaches = true
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(150)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(150)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)
    }

    @Test(dataProvider = "enginesAndOthers")
    fun rename(
        @Suppress("UNUSED_PARAMETER") caseName: String,
        openWith: EngineInfo.UnrealOpenType,
        engine: UnrealEngine
    ) {
        unrealInTestSetup(openWith, engine)
        project = openProject(openWith)

        val virtualFiles = arrayListOf<VirtualFile>()

        val classNameAndPaths = arrayOf(
            Pair("AMyActor", activeSolutionDirectory / "Source" / projectDirectoryName / "MyActor.h"),
            Pair(
                "AMyPluginActor",
                activeSolutionDirectory / "Plugins" / "TestPlugin" / "Source" / "TestPlugin" / "Public" / "MyPluginActor.h"
            )
        )

        for ((className, path) in classNameAndPaths) {
            val propertyName = "bMyProperty"
            val symbolsToRename = arrayOf(propertyName, className, propertyName + "Rename", className + "Rename")
            val virtualFile = renameSymbolsInFile(path.path, symbolsToRename)
            virtualFiles.add(virtualFile.shouldNotBeNull("virtualFile == null for $path"))
        }

        executeWithGold(testGoldFile) {
            for (file in virtualFiles) {
                dumpFile(file, it)
            }
            val iniFiles = FileUtils.listFiles(activeSolutionDirectory, arrayOf("ini"), true)
            for (file in iniFiles) {
                val iniFile = getVirtualFileFromPath(file.path)
                dumpFile(iniFile, it)
            }
        }
    }

    private fun dumpFile(file: VirtualFile, it: PrintStream) {
        it.println("---")
        val relPath = FileUtil.getRelativePath(activeSolutionDirectory, File(file.path))
        it.println(relPath)
        it.println("---")
        val document = FileDocumentManager.getInstance().getDocument(file)
        it.println(document.shouldNotBeNull("Cannot get document for $file").text)
    }

    private fun renameSymbolsInFile(path: String, symbolsToRename: Array<String>): VirtualFile? {
        return withOpenedEditor(project, path) {
            for (symbol in symbolsToRename) {
                renameSymbol(symbol, this)
            }
        }.virtualFile
    }


    private fun renameSymbol(symbolName: String, editor: EditorImpl) {
        logger.debug("rename $symbolName")
        editor.setCaretBeforeWord(symbolName)
        var pageNumber = 0
        withPageWithClickedNext(project,
            function = {
                callActionAndHandlePopup(RiderActions.REFACTOR_THIS, editor.dataContext) {
                    executeItem("Rename")
                }
            },
            pageActions = {
                val page = this as BeRefactoringsPage
                val content = page.content
                when (pageNumber) {
                    0 -> {
                        val newName = content.getBeControlById<BeTextBox>("Rename.Name")
                        newName.text.set(newName.text.value + "Rename")

                        val renameFiles = content.tryGetBeControlById<BeCheckbox>("Rename.RenameFile")
                        renameFiles?.property?.set(false) // TODO fix exceptions and set(true)
                    }

                    1 -> {
                        val coreRedirect = content.getBeControlById<BeCheckbox>("ShouldCoreRedirect")
                        coreRedirect.property.set(true)
                    }

                    else -> {
                        Assert.fail("Unexpected page $page")
                    }
                }
                pageNumber += 1
            })
        waitBackendAndWorkspaceModel(project)
    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, engine: UnrealEngine ->
            "$baseString${engine.id.replace('.', '_')}"
        }

        for (openWith in arrayOf(EngineInfo.UnrealOpenType.Uproject, EngineInfo.UnrealOpenType.Sln)) {
            for (version in arrayOf("4.27", "5.0")) {
                val engine: UnrealEngine = unrealInfo.testingEngines.find { it.id == version && it.isInstalledBuild }!!
                result.add(arrayOf(uniqueDataString("$openWith", engine), openWith, engine))
            }
        }

        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }
}