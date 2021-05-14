package com.jetbrains.rider.test.base

import com.intellij.execution.RunManagerEx
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
import com.jetbrains.rdclient.protocol.getComponent
import com.jetbrains.rider.projectView.SolutionLifecycleHost
import com.jetbrains.rider.protocol.components.ShellHost
import com.jetbrains.rider.protocol.protocolHost
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import java.io.File
import java.time.Duration

/**
 * Use this class for cases that need to close a solution between each test and
 * make a fresh copy for every other test.
 *
 * Opens solution from @TestEnvironment::solution annotation or from getSolutionDirectoryName() method.
 * Close the solution after each method.
 */
abstract class BaseTestWithProject : BaseTestWithProjectBase() {

    private var myProject: Project? = null
    var project: Project
        get() = this.myProject!!
        set(value) {
            this.myProject = value
        }

    protected open val waitForCaches: Boolean
        get() = false

    protected open val persistCaches: Boolean
        get() = false

    protected open val restoreNuGetPackages: Boolean
        get() = false

    protected open val expectedBackendMemoryUsageMb: Int?
        get() = null

    protected open val backendLoadedTimeout: Duration
        get() = Duration.ofSeconds(60)

    protected open val editorGoldFile: File
        get() = File(testCaseGoldDirectory, getOpenedDocumentName(project) ?: testMethod.name)

    @BeforeMethod(alwaysRun = true)
    fun setUpTestCaseSolution() {
        openSolution(testMethod.environment.solution ?: getSolutionDirectoryName())
        (EncodingProjectManagerImpl.getInstance(project) as EncodingProjectManagerImpl).setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.ALWAYS)
    }

    private fun openSolution(solutionDirName: String) {
        GeneralSettings.getInstance().isConfirmExit = false

        val params = OpenSolutionParams()
        params.customSolutionName = getCustomSolutionFileName()
        params.preprocessTempDirectory = ::preprocessTempDirectory
        params.persistCaches = persistCaches
        params.waitForCaches = waitForCaches
        params.restoreNuGetPackages = restoreNuGetPackages
        params.backendLoadedTimeout = backendLoadedTimeout

        useCachedTemplates = false

        try {
            myProject = openSolution(solutionDirName, params)
        } catch (ex: Throwable) {
            logger.error(ex)
            throw ex
        }
    }

    fun withRunProgram(
        timeout: Duration = Duration.ofSeconds(30),
        action : (Project) -> Unit
    ) {
        var projectProcess : ProcessHandler? = null
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

    //todo (could it be in BeforeMethod? BaseTestWithSolution?)
    protected open fun preprocessTempDirectory(tempDir: File) {}

    protected abstract fun getSolutionDirectoryName(): String
    protected open fun getCustomSolutionFileName(): String? = null

    @AfterMethod(alwaysRun = true)
    fun checkMethodMemoryUsage() {
        if (myProject == null || project.isDisposed) return

        val shellModel = project.protocolHost.getComponent<ShellHost>().model
        SolutionLifecycleHost.getInstance(project).waitFullStartup(OpenSolutionParams().initWithCachesTimeout)

        if (expectedBackendMemoryUsageMb != null)
            assertMemoryUsage(shellModel, "Before CloseSolution", expectedBackendMemoryUsageMb!!, testMethod.name)
    }

    @AfterMethod(alwaysRun = true, dependsOnMethods = ["checkMethodMemoryUsage"])
    fun closeSolution() {
        try {
            closeSolutionAndResetSettings(myProject)
        } finally {
            myProject = null
        }
    }
}
