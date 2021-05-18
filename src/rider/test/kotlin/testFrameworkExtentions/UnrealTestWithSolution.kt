package com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions

import com.intellij.execution.RunManagerEx
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
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

    @BeforeClass(alwaysRun = true)
    fun setUpClassSolution() {
        GeneralSettings.getInstance().isConfirmExit = false
        PrepareTestEnvironment.setBuildToolPath(ToolsetVersion.TOOLSET_16_CPP)
        myProject = openSolution(solutionName, openSolutionParams)
    }

    @AfterClass(alwaysRun = true)
    fun closeSolution() {
        try {
            closeSolutionAndResetSettings(myProject)
        } finally {
            myProject = null
        }
    }

    override fun putSolutionToTempTestDir(solutionDirectoryName: String,
                                                solutionFileName: String?,
                                                filter: ((File) -> Boolean)?) : File {
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
                generateSolutionFromUProject()
                File(workDirectory, solutionFileName ?: getDefaultSolutionFileName(workDirectory) ?: "")
            }

        activeSolution = solutionDirectoryName
        frameworkLogger.info("Active solution: '$activeSolution'")

        return solutionFile
    }

    private fun getDefaultSolutionFileName(path: File) : String? {
        return path.listFiles().orEmpty().singleOrNull { a -> a.isFile && a.extension == "slnf" }?.name
            ?: path.listFiles().orEmpty().singleOrNull { a -> a.isFile && a.extension == "sln" }?.name
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

    private fun generateSolutionFromUProject(){
        val myJson = Json { ignoreUnknownKeys = true }

        @Serializable
        data class UprojectData(val EngineAssociation: String)
        @Serializable
        data class InstallInfo(val InstallLocation: String, val AppName: String)
        @Serializable
        data class InstallationInfoList(val InstallationList: List<InstallInfo>)

        val uprojectFile = activeSolutionDirectory.combine(solutionName, "$solutionName.uproject")
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
                    return
                }
            }
            throw Exception("Not found UnrealEngine!\nInstallationInfoList: $installationInfoList")
        }
        throw Exception("Not found $launcherInstallDatPath!")
    }
}