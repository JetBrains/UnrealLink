package testFrameworkExtentions

import com.intellij.execution.RunManagerEx
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.GeneralSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rdclient.notifications.NotificationsHost
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.base.BaseTestWithSolutionBase
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.framework.getPersistentCacheFolder
import com.jetbrains.rider.test.scriptingApi.setReSharperBoolSetting
import com.jetbrains.rider.test.scriptingApi.startRunConfigurationProcess
import com.jetbrains.rider.test.scriptingApi.stop
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import com.jetbrains.rider.test.framework.getFileWithExtension as getFileWithExtensionRd

/** Class for Unreal tests.
 * Contains unreal-specific properties and methods.
 * Before each test clear project puts in temp directory.
 * In/before test unreal engine version switch is started.
 * If necessary, solution generator is activated.
 */
abstract class UnrealTestProject : BaseTestWithSolutionBase() {
    private var myProject: Project? = null

    var project: Project
        get() = this.myProject!!
        set(value) {
            this.myProject = value
        }

    /**
     * Params for solution/project. Overrides in/before concrete suite/test.
     */
    val openSolutionParams: OpenSolutionParams = OpenSolutionParams()
    lateinit var projectDirectoryName: String

    /**
     * Uproject file object. Initialized in [testSetup]
     */
    lateinit var uprojectFile: File

    /**
     * Property represents object with unreal-specific information about suite/test.
     */
    lateinit var unrealInfo : EngineInfo

    @BeforeClass(alwaysRun = true)
    fun suiteSetup() {
        unrealInfo = EngineInfo()
        logger.debug("Found engines ${unrealInfo.engineList}")
        GeneralSettings.getInstance().isConfirmExit = false
    }

    @BeforeMethod
    open fun testSetup() {
        uprojectFile = putSolutionToTempTestDir(projectDirectoryName, "$projectDirectoryName.uproject")
        setReSharperBoolSetting("CppUnrealEngine/IndexEngine", false)
    }

    @AfterMethod(alwaysRun = true)
    fun testTeardown() {
        try {
            closeSolution(myProject)
            val tempDirDelResult = FileUtil.delete(tempTestDirectory)
            if (!tempDirDelResult)
                frameworkLogger.warn("Error deleting '$tempTestDirectory' folder")

            // Clearing engine after test
            if (unrealInfo.needInstallRiderLink && unrealInfo.placeToInstallRiderLink == PluginInstallLocation.Engine) {
                val unrealLinkDelResult = FileUtil.delete(unrealInfo.pathToRiderLinkInEngine)
                if (!unrealLinkDelResult)
                    frameworkLogger.warn("Error deleting '${unrealInfo.pathToRiderLinkInEngine}' folder")
            }
        } finally {
            myProject = null
        }
    }

    // TODO: refactor existent openProject's function and replace
    fun openProject(openWith: EngineInfo.UnrealOpenType): Project {
        val params = openSolutionParams // TODO

        // TODO create function in [BaseTestWithSolutionBase]
        NotificationsHost.registerConsumer(solutionNotificationSequentialLifetime.next()) {
            notificationList.add(it)
            if (it.type in arrayOf(NotificationType.ERROR, NotificationType.WARNING)) {
                val notificationMessage = it.title + ": " + it.content
                frameworkLogger.warn(notificationMessage)
            }
            if (it.title.startsWith("Unable to locate .NET Core SDK")) {
                frameworkLogger.error(it.title + ": " + it.content)
            }
            if (it.title.startsWith("Rider was unable to connect to MSBuild")) {
                frameworkLogger.error(it.title + ". Try to delete and download again the MsBuild and .NET SDK from " + getPersistentCacheFolder())
            }
        }
        return doOpenSolution(getProjectFile(openWith), params)
    }

    private fun getProjectFile(openWith: EngineInfo.UnrealOpenType): File {
        if (openWith == EngineInfo.UnrealOpenType.Uproject)
            return uprojectFile

        return uprojectFile.getFileWithExtensionRd(".sln")
    }

    // TODO Take out in framework?
    fun withRunProgram(
        timeout: Duration = Duration.ofSeconds(30),
        action: (Project) -> Unit
    ) {
        var projectProcess: ProcessHandler? = null
        try {
            val runManagerEx = RunManagerEx.getInstanceEx(project)
            val settings = runManagerEx.selectedConfiguration
                ?: throw AssertionError("No configuration selected")
            projectProcess = startRunConfigurationProcess(project, settings, timeout)
            action(project)
        } finally {
            projectProcess!!.stop()
        }
    }

//    TODO:
//     protected open fun prepareUproject(
//        uprojectFile: File,
//        engine: UnrealEngine,
//        openWith: UnrealTestInfo.UnrealOpenType,
//        riderLinkLocation: PluginInstallLocation)

    fun installRiderLink(place: PluginInstallLocation, timeout: Duration = Duration.ofSeconds(180)) {
        var riderLinkInstalled = false
        project.solution.rdRiderModel.installPluginFinished.advise(Lifetime.Eternal) { riderLinkInstalled = true }
        project.solution.rdRiderModel.installEditorPlugin.fire(
            InstallPluginDescription(place, ForceInstall.Yes)
        )
        waitAndPump(timeout, { riderLinkInstalled }, { "RiderLink did not install" })
    }

    protected fun replaceUnrealEngineVersionInUproject(uprojectFile: File, engine: UnrealEngine) {
        val uprojectText = uprojectFile.readText()
            .replace("\"EngineAssociation\": \".*\",".toRegex(), "\"EngineAssociation\": \"${engine.id}\",")
        uprojectFile.writeText(uprojectText)
        println("Content of final UProject: \n${uprojectText}")
    }

    protected fun generateSolutionFromUProject(uprojectFile: File) {
        val ue5specific = if (unrealInfo.currentEngine!!.version.major > 4) "UnrealBuildTool\\" else ""
        val engineType = if (unrealInfo.currentEngine!!.isInstalledBuild) "-rocket" else "-engine"
        val ubtCommand = "${unrealInfo.currentEngine!!.path}\\Engine\\Binaries\\DotNET\\${ue5specific}UnrealBuildTool.exe " +
                "-ProjectFiles -game -progress $engineType -project=\"${uprojectFile.absolutePath}\""
        ProcessBuilder(*(ubtCommand).split(" ").toTypedArray())
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor(90, TimeUnit.SECONDS)
    }

    fun unrealInTestSetup(openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInfo.currentEngine = engine

        println("Test starting with $engine, opening by $openWith.")

        replaceUnrealEngineVersionInUproject(uprojectFile, unrealInfo.currentEngine!!)

        if (openWith == EngineInfo.UnrealOpenType.Sln) {
            generateSolutionFromUProject(uprojectFile)
            openSolutionParams.minimalCountProjectsMustBeLoaded = null
        } else {
            openSolutionParams.minimalCountProjectsMustBeLoaded =
                1400 // TODO: replace the magic number with something normal
        }

        project = openProject(openWith)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)
    }
}