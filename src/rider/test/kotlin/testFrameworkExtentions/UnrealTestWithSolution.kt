package com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions

import com.intellij.execution.RunManagerEx
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import com.jetbrains.rider.test.base.BaseTestWithSolutionBase
import com.jetbrains.rider.test.base.PrepareTestEnvironment
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.combine
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.framework.prepareSolutionFromZip
import com.jetbrains.rider.test.scriptingApi.startRunConfigurationProcess
import com.jetbrains.rider.test.scriptingApi.stop
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

abstract class UnrealTestWithSolution : BaseTestWithSolutionBase() {

    private var myProject: Project? = null

    var project: Project
        get() = this.myProject!!
        set(value) {
            this.myProject = value
        }

    lateinit var solutionName: String
    val openSolutionParams: OpenSolutionParams = OpenSolutionParams()

    override val testCaseNameToTempDir: String
        get() = this.javaClass.simpleName

    override val clearCaches: Boolean
        get() = false

    var installRiderLink = false

    @BeforeClass(alwaysRun = true)
    fun setUpClassSolution() {
        GeneralSettings.getInstance().isConfirmExit = false
        PrepareTestEnvironment.setBuildToolPath(ToolsetVersion.TOOLSET_16_CPP)
        myProject = openSolution(solutionName, openSolutionParams)

        waitAndPump(Duration.ofSeconds(15),
            { project.solution.rdRiderModel.isUnrealEngineSolution.value }, { "This is not unreal solution" })

        if (installRiderLink) {
            var riderLinkInstalled = false
            project.solution.rdRiderModel.installPluginFinished.advise(Lifetime.Eternal) { riderLinkInstalled = true }
            project.solution.rdRiderModel.installEditorPlugin.fire(
                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes)
            )

            waitAndPump(Duration.ofSeconds(90), { riderLinkInstalled }, { "RiderLink did not install" })
        }
    }

    @AfterClass(alwaysRun = true)
    fun closeSolution() {
        try {
            closeSolutionAndResetSettings(myProject)
        } finally {
            myProject = null
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

    protected fun generateSolutionFromUProject(solutionFile: File): File {
        val myJson = Json { ignoreUnknownKeys = true }

        @Serializable
        data class UprojectData(val EngineAssociation: String)
        @Serializable
        data class InstallInfo(val InstallLocation: String, val AppName: String)
        @Serializable
        data class InstallationInfoList(val InstallationList: List<InstallInfo>)

        val uprojectFile = File(activeSolutionDirectory, "$solutionName.uproject")
        val uprojectData = myJson.decodeFromString<UprojectData>(uprojectFile.readText())

        val launcherInstallDatPath = File(System.getenv("ProgramData")).combine("Epic", "UnrealEngineLauncher", "LauncherInstalled.dat")
        if (launcherInstallDatPath.exists()) {
            val installationInfoList = myJson.decodeFromString<InstallationInfoList>(launcherInstallDatPath.readText())
            installationInfoList.InstallationList.forEach {
                if (it.AppName.equals("UE_${uprojectData.EngineAssociation}", ignoreCase = true)) {
                    val ubtCommand = "${it.InstallLocation}\\Engine\\Binaries\\DotNET\\UnrealBuildTool.exe " +
                            "-ProjectFiles -UsePrecompiled -Game \"${uprojectFile.absolutePath}\""
                    ProcessBuilder(*(ubtCommand).split(" ").toTypedArray())
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start()
                        .waitFor(90, TimeUnit.SECONDS)
                    return File(solutionFile,"$solutionName.sln")
                }
            }
            throw Exception("Not found UnrealEngine!\nInstallationInfoList: $installationInfoList")
        }
        throw Exception("Not found $launcherInstallDatPath!")
    }
}