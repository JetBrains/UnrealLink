package com.jetbrains.rider.test.base

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.util.SmartList
import com.intellij.util.TimedReference
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rdclient.notifications.NotificationsHost
import com.jetbrains.rdclient.util.idea.toVirtualFile
import com.jetbrains.rider.debugger.settings.DotNetDebuggerSettings
import com.jetbrains.rider.model.RdNuGetNotificationContext
import com.jetbrains.rider.projectView.SolutionLifecycleHost
import com.jetbrains.rider.projectView.SolutionManager
import com.jetbrains.rider.protocol.ProtocolManager
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import com.jetbrains.rider.test.framework.*
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.AfterClass
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import java.io.File
import java.time.Duration

/**
 * Base class for work with solution.
 *
 * Use withSolution() API to specify a solution to use in a particular test. The solution is copied into a temp directory.
 * Solution is not shared between tests. The solution is closed aftrer test is finished.
 */
abstract class BaseTestWithProjectBase : BaseTestWithShell() {

    /**
     * Default parameters to open solution in test
     */
    class OpenSolutionParams {
        var customSolutionName : String? = null
        var preprocessTempDirectory : ((File) -> Unit)? = null
        var preprocessSolutionFile : ((File) -> File)? = null
        var persistCaches : Boolean = false
        var addBomForNewFiles : Boolean = false
        var restoreNuGetPackages : Boolean = false

        var waitForCaches : Boolean = false
        var waitForSolutionBuilder : Boolean = false
        var waitForBackendTypingAssists : Boolean = false

        var backendLoadedTimeout: Duration = Duration.ofSeconds(60)
        var projectModelReadyTimeout: Duration = Duration.ofSeconds(60)
        var initWithCachesTimeout: Duration = Duration.ofSeconds(60)
    }

    init {
        // Do not try to find leaking projects, our tests are not ready for that
        System.setProperty("idea.log.leaked.projects.in.tests", "false")
        System.setProperty("javascript.linters.prevent.detection", "true")
    }

    companion object {

        const val solutionSourceRootName = "solutions"
        val solutionSourceRootDirectory: File = testDataDirectory.combine(solutionSourceRootName)

        fun doOpenSolution(
            solutionFile: File,
            params: OpenSolutionParams
        ) : Project {

            frameworkLogger.info("Start opening solution: '${solutionFile.name}'")

            persistSolutionCaches(params.persistCaches)

            val project = requestOpenExistingProject(solutionFile, params.restoreNuGetPackages)!!
            val encodingProjectManagerImpl = EncodingProjectManager.getInstance(project) as EncodingProjectManagerImpl
            val bomOption = if (params.addBomForNewFiles) EncodingProjectManagerImpl.BOMForNewUTF8Files.ALWAYS else EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER
            encodingProjectManagerImpl.setBOMForNewUtf8Files(bomOption)
            project.enableBackendAsserts()

            waitForSolution(project, params)

            frameworkLogger.info("Solution: '${project.name}' is opened and ready")

            if (!ProtocolManager.isResharperBackendDisabled()) {
                assertCurrentSolutionToolset()
            }

            // dispose all timed objects to prevent dispose of them later at random point during the test
            application.runWriteAction {
                TimedReference.disposeTimed()
                TimedReference.disposeTimed()
            }

            return project
        }

        fun requestOpenExistingProject(solutionFile: File, restore: Boolean = false) : Project? {
            val solutionVirtualFile = VfsUtil.findFileByIoFile(solutionFile, true) ?: return null
            return SolutionManager.openExistingProject(null, false, solutionVirtualFile, restore)
        }

        fun waitForSolution(project: Project, params: OpenSolutionParams) {
            //wait for project model
            waitForBackendWillBeLoaded(project, params.backendLoadedTimeout, params.projectModelReadyTimeout)
            flushQueues()

            application.saveAll() //persist files in .idea and .iml files on disk

            assertAllProjectsWereLoaded(project)
            if (!ProtocolManager.isResharperBackendDisabled()) {
                assertSolutionWasReloadedTimes(project, 1)
            }
            if (params.restoreNuGetPackages)
                checkNuGetOperationStatus(project, RdNuGetNotificationContext.Restore)

            TestApplicationManager.getInstance().setDataProvider(object : TestDataProvider(project) {
                override fun getData(dataId: String): Any? {
                    return super.getData(dataId)
                }
            })

            if (params.waitForBackendTypingAssists)
                SolutionLifecycleHost.getInstance(project).waitForBackendTypingAssists(Duration.ofSeconds(60))

            if (params.waitForCaches) //wait for psi caches
                SolutionLifecycleHost.getInstance(project).waitFullStartup(params.initWithCachesTimeout)

            if (params.waitForSolutionBuilder)
                SolutionLifecycleHost.getInstance(project).waitSolutionBuilderIsReady()
        }
    }

    var activeSolution: String = ""
    val activeSolutionDirectory
        get() = tempTestDirectory.resolve(activeSolution)

    open val clearCaches: Boolean
        get() = true

    val notificationList = mutableListOf<Notification>()
    private var solutionNotificationSequentialLifetime = SequentialLifetimes(Lifetime.Eternal)

    @AfterMethod(alwaysRun = true)
    fun tearDownTestCaseSolutionBase() {
        solutionNotificationSequentialLifetime.terminateCurrent()
        if (clearCaches) {
            testCaseNameToTempDirCache.clear()
        }
        WorkspaceModelImpl.forceEnableCaching = false
    }

    fun withSolution(
        solutionDirectoryName: String,
        preprocessTempDirectory: ((File) -> Unit)?,
        action: (Project) -> Unit
    ) = withSolution(solutionDirectoryName, OpenSolutionParams(), preprocessTempDirectory, action)


    fun withSolution(
        solutionDirectoryName: String,
        openParams: OpenSolutionParams,
        preprocessTempDirectory: ((File) -> Unit)?,
        action: (Project) -> Unit
    ) = withSolution(solutionDirectoryName, openParams.also { it.preprocessTempDirectory = preprocessTempDirectory }, action)

    fun withSolution(
        solutionDirectoryName: String,
        openParams: OpenSolutionParams,
        action: (Project) -> Unit
    ) {
        var project: Project? = null
        try {
            project = openSolution(solutionDirectoryName, openParams)
            action(project)
        } finally {
            closeSolution(project)
        }
    }

    protected fun withSolutionOpenedFromProject(solutionDirectoryName: String,
                                                openParams: OpenSolutionParams,
                                                relPathToProjectFile: String,
                                                action: (Project) -> Unit) {

        putSolutionToTempTestDir(solutionDirectoryName, openParams.customSolutionName)
        openParams.preprocessTempDirectory?.invoke(activeSolutionDirectory) //check

        persistSolutionCaches(openParams.persistCaches)

        //open project file
        val projFile = File(activeSolutionDirectory, relPathToProjectFile).toVirtualFile(true)!!
        val provider = ProjectOpenProcessor.getImportProvider(projFile)!!
        val project = provider.doOpenProject(projFile, null, false)!!

        project.enableBackendAsserts()

        waitForSolution(project, openParams)

        try { action(project) }
        finally { closeSolution(project) }
    }

    protected fun openSolution(
        solutionDirectoryName: String,
        params: OpenSolutionParams
    ): Project {

        //nuget or just copy
        val solutionFile = putSolutionToTempTestDir(solutionDirectoryName, params.customSolutionName)
        params.preprocessTempDirectory?.invoke(activeSolutionDirectory)
        val solutionFilePreprocessor = params.preprocessSolutionFile

        val preProcessedSolutionFile = if (solutionFilePreprocessor != null) solutionFilePreprocessor(solutionFile) else solutionFile

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

        return doOpenSolution(preProcessedSolutionFile, params)
    }

    fun closeSolution(project: Project?) {
        frameworkLogger.info("Start closing a solution: '${project?.name}'")
        val exceptions = SmartList<Throwable>()

        // close all open editors first
        if (project != null && !project.isDisposed) {
            try {
                waitForDaemonCloseAllOpenEditors(project)
            } catch(t: Throwable) {
                exceptions.add(t)
            }
        }

        // set data provider to null first, because getting the provider on disposed project may throw exceptions
        TestApplicationManager.getInstance().setDataProvider(null)
        try {
            closeProjectsWaitForBackendWillBeClosed(Duration.ofSeconds(60), true, true)
        } catch(t: Throwable) {
            exceptions.add(t)
        }

        // throw all collected exceptions
        CompoundRuntimeException.throwIfNotEmpty(exceptions)
    }

    /**
     * Prepare solution work directory
     *
     * @param solutionDirectoryName - source solution directory name
     * @param solutionFileName - source solution file name
     * @param filter - filter for copying source to work directory
     *
     * @return solution file or directory if sln file is filtered out
     */
    protected open fun putSolutionToTempTestDir(solutionDirectoryName: String,
                                                solutionFileName: String?,
                                                filter: ((File) -> Boolean)? = null) : File {

        val workDirectory = File(tempTestDirectory, solutionDirectoryName)
        val sourceDirectory = File(solutionSourceRootDirectory, solutionDirectoryName)

        val solutionFile =
            if (workDirectory.exists() && !solutionFileName.isNullOrEmpty()) {
                // Solution folder already exists
                workDirectory.walk().find { it.name == solutionFileName }
                    .shouldNotBeNull("Cannot find *.sln file in the directory: '$workDirectory'")
            } else if (File(sourceDirectory, "testdata.config").exists()) {
                // ZIP from repo
                prepareSolutionFromZip(solutionSourceRootDirectory, tempTestDirectory, solutionDirectoryName)
                tempTestDirectory.walk().filter { file -> file.extension == "sln" }.single()
            } else {
                // Copy solution from sources
                FileUtil.copyDir(sourceDirectory, workDirectory, filter)

                workDirectory.isDirectory.shouldBeTrue("Expected '${workDirectory.absolutePath}' to be a directory")
                File(workDirectory, solutionFileName ?: getDefaultSolutionFileName(workDirectory) ?: "")
            }

        activeSolution = solutionDirectoryName
        frameworkLogger.info("Active solution: '$activeSolution'")

        return solutionFile
    }

    protected infix fun String.copySourceFileTo(destination: File) =
        (testCaseSourceDirectory / this).copyRecursively(destination, true)

    /**
     * Store current solution state to a folder inside tempTestDirectory
     */
    protected fun storeCurrentSolutionState() {
        try {
            val copyToFolderSuffix = if (testMethod.currentInvocation > 0) "_${testMethod.currentInvocation}" else ""
            val targetDirectory = File(tempTestDirectory.parent, testMethod.name + copyToFolderSuffix)
            frameworkLogger.debug("Copy $tempTestDirectory -> $targetDirectory")
            tempTestDirectory.copyRecursively(targetDirectory)
        } catch (ex: Exception) {
            frameworkLogger.error(ex)

            if (ex is AccessDeniedException) {
                frameworkLogger.info("AccessDeniedException:")
                frameworkLogger.info("Message: " + ex.message)
                frameworkLogger.info("Reason: " + ex.reason)
                frameworkLogger.info("Suppressed: " + ex.suppressed.joinToString("; ") { it.message ?: "<NULL>" })
            }

            val builder = StringBuilder()
            dumpFolderContent(tempTestDirectory, builder)
            frameworkLogger.debug(builder.toString())
        }
    }

    private fun dumpFolderContent(file: File, builder : StringBuilder) {
        builder.append(file.path)
        builder.append(" ")
        if (file.isDirectory) {
            builder.appendLine("DIR")
            val children = file.listFiles()
            if (children == null) builder.appendLine("CHILDREN = NULL")
            else {
                for (child in children) {
                    dumpFolderContent(child, builder)
                }
            }
        }
        else {
            builder.appendLine("FILE")
        }
    }

    protected fun getDefaultSolutionFileName(path: File) : String? {
        return path.listFiles().orEmpty().singleOrNull { a -> a.isFile && a.extension == "slnf" }?.name
            ?: path.listFiles().orEmpty().singleOrNull { a -> a.isFile && a.extension == "sln" }?.name
    }

    protected fun closeSolutionAndResetSettings(project: Project?) {
        closeSolution(project)
    }

    @AfterClass
    fun tearDownClassSolutionBase() {
        persistSolutionCaches(false)
    }

    @BeforeMethod(alwaysRun = true)
    fun overrideDebuggerSettings() {
        restoreAllDebuggerSettings()
        // For deterministic behavior
        DotNetDebuggerSettings.instance.showReturnValues = false
    }
}
