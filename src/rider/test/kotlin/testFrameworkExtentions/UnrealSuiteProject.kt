//package com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions
//
//import com.intellij.execution.RunManagerEx
//import com.intellij.execution.process.ProcessHandler
//import com.intellij.ide.GeneralSettings
//import com.intellij.openapi.project.Project
//import com.jetbrains.rd.ide.model.UnrealEngine
//import com.jetbrains.rd.ide.model.UnrealShellModel
//import com.jetbrains.rd.ide.model.UnrealVersion
//import com.jetbrains.rd.platform.util.application
//import com.jetbrains.rd.util.lifetime.Lifetime
//import com.jetbrains.rdclient.util.idea.waitAndPump
//import com.jetbrains.rider.cpp.unreal.UnrealShellHost
//import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
//import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
//import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
//import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
//import com.jetbrains.rider.projectView.solution
//import com.jetbrains.rider.test.base.BaseTestWithSolutionBase
//import com.jetbrains.rider.test.base.PrepareTestEnvironment
//import com.jetbrains.rider.test.enums.ToolsetVersion
//import com.jetbrains.rider.test.framework.combine
//import com.jetbrains.rider.test.protocol.testProtocolHost
//import com.jetbrains.rider.test.scriptingApi.startRunConfigurationProcess
//import com.jetbrains.rider.test.scriptingApi.stop
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.decodeFromString
//import kotlinx.serialization.json.Json
//import org.testng.annotations.AfterClass
//import org.testng.annotations.BeforeClass
//import java.io.File
//import java.time.Duration
//import java.util.concurrent.TimeUnit
//
///** Class for Unreal testing
// * TODO: create more documentation */
//abstract class UnrealSuiteProject : BaseTestWithSolutionBase() {
//
//    private var myProject: Project? = null
//
//    var project: Project
//        get() = this.myProject!!
//        set(value) {
//            this.myProject = value
//        }
//
//    /**
//     * Params for solution/project. Overrides in/before concrete suite/test.
//     */
//    val openSolutionParams: OpenSolutionParams = OpenSolutionParams()
//    lateinit var projectDirectoryName: String
//
//    /**
//     * Uproject file object. Initialized in [testSetup]
//     */
//    lateinit var uprojectFile: File
//
//    /**
//     * Unreal Engine's versions which will be used in tests.
//     * Tests generate base on this data. Can be expanded.
//     */
//    protected val testingVersions = arrayOf(
//        UnrealVersion(4, 26, 2),
//        UnrealVersion(5, 0, 0)
//    )
//
//    /**
//     * Property represents object with unreal-specific information about suite/test.
//     */
//    lateinit var unrealInfo : UnrealTestInfo
//
//    override val testCaseNameToTempDir: String
//        get() = this.javaClass.simpleName
//
//    @BeforeClass(alwaysRun = true)
//    fun setUpClassSolution() {
////        testingEngines = engineList.filter { it.version in testingVersions }
////        println("Unreal Engines $testingEngines will be used")
//
//        GeneralSettings.getInstance().isConfirmExit = false
//        PrepareTestEnvironment.setBuildToolPath(ToolsetVersion.TOOLSET_16_CPP)
//        myProject = openSolution(solutionName, openSolutionParams)
//
//        waitAndPump(Duration.ofSeconds(15),
//            { project.solution.rdRiderModel.isUnrealEngineSolution.value }, { "This is not unreal solution" })
//
//        if (needInstallRiderLink) {
//            var riderLinkInstalled = false
//            project.solution.rdRiderModel.installPluginFinished.advise(Lifetime.Eternal) { riderLinkInstalled = true }
//            project.solution.rdRiderModel.installEditorPlugin.fire(
//                InstallPluginDescription(placeToInstallRiderLink, ForceInstall.Yes))
//            waitAndPump(Duration.ofSeconds(120), { riderLinkInstalled }, { "RiderLink did not install" })
//        }
//    }
//
//    @AfterClass(alwaysRun = true)
//    fun closeSolution() {
//        try {
//            closeSolutionAndResetSettings(myProject)
//        } finally {
//            myProject = null
//        }
//    }
//
//    fun withRunProgram(
//        timeout: Duration = Duration.ofSeconds(30),
//        action : (Project) -> Unit
//    ) {
//        var projectProcess : ProcessHandler? = null
//        try {
//            val runManagerEx = RunManagerEx.getInstanceEx(project)
//            val settings = runManagerEx.selectedConfiguration
//                ?: throw AssertionError("No configuration selected")
//            projectProcess = startRunConfigurationProcess(project, settings, timeout)
//            action(project)
//        } finally {
//            projectProcess!!.stop()
//        }
//    }
//
//    protected fun switchUnrealEngineVersionInUproject(){
//        val uprojectFile = File(activeSolutionDirectory, "$solutionName.uproject")
//        val uprojectText = uprojectFile.readText()
//            .replace("\"EngineAssociation\": \".*\",","\"EngineAssociation\": \"${currentEngine.id}\",")
//        uprojectFile.writeText(uprojectText)
//        println("QQQQQQQQQQQQQQQQQQQQQQQQQQQ. ${uprojectFile.readText()}")
//    }
//
//    protected fun generateSolutionFromUProject(solutionFile: File): File{
//        switchUnrealEngineVersionInUproject()
//
//        val uprojectFile = File(activeSolutionDirectory, "$solutionName.uproject")
//        val ubtCommand = "${currentEngine.path}\\Engine\\Binaries\\DotNET\\UnrealBuildTool.exe " +
//                "-ProjectFiles -UsePrecompiled -Game \"${uprojectFile.absolutePath}\""
//        ProcessBuilder(*(ubtCommand).split(" ").toTypedArray())
//            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
//            .redirectError(ProcessBuilder.Redirect.INHERIT)
//            .start()
//            .waitFor(90, TimeUnit.SECONDS)
//        return File(solutionFile,"$solutionName.sln")
//    }
//
////    protected fun generateSolutionFromUProject(solutionFile: File): File {
////        val myJson = Json { ignoreUnknownKeys = true }
////
////        @Serializable
////        data class UprojectData(val EngineAssociation: String)
////        @Serializable
////        data class InstallInfo(val InstallLocation: String, val AppName: String)
////        @Serializable
////        data class InstallationInfoList(val InstallationList: List<InstallInfo>)
////
////        val uprojectFile = File(activeSolutionDirectory, "$solutionName.uproject")
////        val uprojectData = myJson.decodeFromString<UprojectData>(uprojectFile.readText())
////
////        val launcherInstallDatPath = File(System.getenv("ProgramData")).combine("Epic", "UnrealEngineLauncher", "LauncherInstalled.dat")
////        if (launcherInstallDatPath.exists()) {
////            val installationInfoList = myJson.decodeFromString<InstallationInfoList>(launcherInstallDatPath.readText())
////            installationInfoList.InstallationList.forEach {
////                if (it.AppName.equals("UE_${uprojectData.EngineAssociation}", ignoreCase = true)) {
////                    val ubtCommand = "${it.InstallLocation}\\Engine\\Binaries\\DotNET\\UnrealBuildTool.exe " +
////                            "-ProjectFiles -UsePrecompiled -Game \"${uprojectFile.absolutePath}\""
////                    ProcessBuilder(*(ubtCommand).split(" ").toTypedArray())
////                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
////                        .redirectError(ProcessBuilder.Redirect.INHERIT)
////                        .start()
////                        .waitFor(90, TimeUnit.SECONDS)
////                    return File(solutionFile,"$solutionName.sln")
////                }
////            }
////            throw Exception("Not found UnrealEngine!\nInstallationInfoList: $installationInfoList")
////        }
////        throw Exception("Not found $launcherInstallDatPath!")
////    }
//}