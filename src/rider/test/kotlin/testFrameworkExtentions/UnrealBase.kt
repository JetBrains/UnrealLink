package testFrameworkExtentions

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.execution.RunManagerEx
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.GeneralSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
import com.intellij.util.TimedReference
import com.intellij.util.application
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rdclient.notifications.NotificationsHost
import com.jetbrains.rdclient.util.idea.toIOFile
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.base.BaseTestWithSolutionBase
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.framework.getPersistentCacheFolder
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.BeforeClass
import org.testng.annotations.DataProvider
import testFrameworkExtentions.suplementary.UprojectData
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit
import com.jetbrains.rider.test.framework.getFileWithExtension as getFileWithExtensionRd

/** Class for Unreal tests.
 * Contains unreal-specific properties and methods.
 * Before each test clear project puts in temp directory.
 * In/before test unreal engine version switch is started.
 * If necessary, solution generator is activated.
 */
abstract class UnrealBase : BaseTestWithSolutionBase() {
    private var myProject: Project? = null

    var project: Project
        get() = this.myProject!!
        set(value) {
            this.myProject = value
        }

    /**
     * Default settings for opening solution/project. Overrides in/before concrete suite/test.
     */
    open val openSolutionParams: OpenSolutionParams =
        OpenSolutionParams().apply {
            waitForCaches = true
            projectModelReadyTimeout = Duration.ofSeconds(150)
            initWithCachesTimeout = Duration.ofSeconds(120)
        }
    lateinit var projectDirectoryName: String

    /**
     * Uproject file object. Initialized in [testSetup]
     */
    lateinit var uprojectFile: File

    /**
     * Property represents object with unreal-specific information about suite/test.
     */
    lateinit var unrealInfo: EngineInfo

    val buildTimeout: Duration
        get() = if (unrealInfo.currentEngine!!.isInstalledBuild)
            Duration.ofMinutes(5)
        else Duration.ofMinutes(15)

    var disableEnginePlugins = true
    var disableEngineIndexing = true

    @BeforeClass(alwaysRun = true)
    fun suiteSetup() {
        unrealInfo = EngineInfo()
        frameworkLogger.info("Found installed engines: ${unrealInfo.installedEngineList}")
        GeneralSettings.getInstance().isConfirmExit = false
    }

    open fun configureSettings() {
        if (disableEngineIndexing)
            setReSharperBoolSetting("CppUnrealEngine/IndexEngine", false)
        else
            setReSharperBoolSetting("CppUnrealEngine/IndexEngine", true)
    }

    open fun putSolutionToTempDir() {
        uprojectFile = putSolutionToTempTestDir(projectDirectoryName, "$projectDirectoryName.uproject")
    }

    open fun prepareAndOpenSolution(parameters: Array<Any>) {
        val openSolutionWithParam = parameters[1] as EngineInfo.UnrealOpenType
        val engineParam = parameters[2] as UnrealEngine
        frameworkLogger.info("Starting open solution (uproject)")
        configureAndOpenUnrealProject(openSolutionWithParam, engineParam, disableEnginePlugins)
    }

    protected fun clearRiderLinkFromEngine() {
        if (unrealInfo.needInstallRiderLink && unrealInfo.placeToInstallRiderLink == PluginInstallLocation.Engine) {
            val unrealLinkDelResult = FileUtil.delete(unrealInfo.pathToRiderLinkInEngine)
            if (!unrealLinkDelResult)
                frameworkLogger.warn("Error deleting '${unrealInfo.pathToRiderLinkInEngine}' folder")
        }
    }

    protected fun collectUnrealLogs() {
        val unrealLogsDir = activeSolutionDirectory.resolve("Saved")
        if (unrealLogsDir.exists()) {
            unrealLogsDir.copyRecursively(testMethod.logDirectory.resolve("UnrealInfo"))
        }
    }

    /** А оно вообще надо? */
    protected fun deleteTempDir() {
        val tempDirDelResult = FileUtil.delete(tempTestDirectory)
        if (!tempDirDelResult)
            frameworkLogger.warn("Error deleting '$tempTestDirectory' folder")
    }

    protected fun closeProject() {
        try {
            closeSolution(myProject)
        } finally {
            myProject = null
        }
    }

    protected fun prepareUnrealProject(
        openWith: EngineInfo.UnrealOpenType,
        engine: UnrealEngine
    ) {
        unrealInfo.currentEngine = engine

        println("Test starting with $engine, opening by $openWith.")

        prepareUprojectFile(uprojectFile, engine, disableEnginePlugins)

        if (engine.isInstalledBuild)
            openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(400)
        else
            openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(600)

        if (openWith == EngineInfo.UnrealOpenType.Sln) {
            generateSolutionFromUProject(uprojectFile)
            openSolutionParams.minimalCountProjectsMustBeLoaded = null
        } else {
            openSolutionParams.minimalCountProjectsMustBeLoaded =
                1400 // TODO: replace the magic number with something normal
        }
    }
    
    protected fun configureAndOpenUnrealProject(
        openWith: EngineInfo.UnrealOpenType,
        engine: UnrealEngine,
        disableEnginePlugins: Boolean
    ) {
        prepareUnrealProject(openWith, engine)

        project = openProject(openWith)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)
    }

    // TODO: refactor existent openProject's function and replace
    protected fun openProject(openWith: EngineInfo.UnrealOpenType): Project {
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

    private fun doOpenSolution(
        solutionFile: File,
        params: OpenSolutionParams
    ): Project {

        frameworkLogger.info("Start opening solution: '${solutionFile.name}'")

        persistSolutionCaches(params.persistCaches)

        val project = requestOpenExistingSolutionOrProject(
            solutionFile,
            params.restoreNuGetPackages,
            params.forceOpenInNewFrame
        )!!
        val encodingProjectManagerImpl = EncodingProjectManager.getInstance(project) as EncodingProjectManagerImpl
        val bomOption =
            if (params.addBomForNewFiles) EncodingProjectManagerImpl.BOMForNewUTF8Files.ALWAYS else EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER
        encodingProjectManagerImpl.setBOMForNewUtf8Files(bomOption)
        project.enableBackendAsserts()

        waitForSolution(project, params)

        frameworkLogger.info("Solution: '${project.name}' is opened and ready")

//        if (!ProtocolManager.isResharperBackendDisabled()) {
//            assertCurrentSolutionToolset(project)
//        }

        // dispose all timed objects to prevent dispose of them later at random point during the test
        application.runWriteAction {
            TimedReference.disposeTimed()
            TimedReference.disposeTimed()
        }

        return project
    }

    private fun getProjectFile(openWith: EngineInfo.UnrealOpenType): File {
        if (openWith == EngineInfo.UnrealOpenType.Uproject)
            return uprojectFile

        return uprojectFile.getFileWithExtensionRd(".sln")
    }

    // TODO Take out in framework?
    fun withRunProgram(
        timeout: Duration = Duration.ofSeconds(30),
        configurationName: String? = null,
        action: (Project) -> Unit
    ) {
        var projectProcess: ProcessHandler? = null
        try {
            val runManagerEx = RunManagerEx.getInstanceEx(project)
            if (configurationName != null)
                runManagerEx.selectedConfiguration = runManagerEx.allSettings.single { it.name == configurationName }
            val settings = runManagerEx.selectedConfiguration
                ?: throw AssertionError("No configuration selected")
            projectProcess = startRunConfigurationProcess(project, settings, timeout)
            action(project)
        } finally {
            projectProcess!!.stop()
        }
    }

    fun installRiderLink(place: PluginInstallLocation, timeout: Duration = Duration.ofSeconds(240)) {
        var riderLinkInstalled = false
        project.solution.rdRiderModel.installPluginFinished.advise(Lifetime.Eternal) { riderLinkInstalled = true }
        project.solution.rdRiderModel.installEditorPlugin.fire(
            InstallPluginDescription(place, ForceInstall.Yes)
        )
        waitAndPump(timeout, { riderLinkInstalled }, { "RiderLink did not install" })
    }

    protected fun prepareUprojectFile(uprojectFile: File, engine: UnrealEngine, disableEnginePlugins: Boolean = true) {
        val mapper = jacksonObjectMapper()
        val uprojectData = mapper.readValue<UprojectData>(uprojectFile.readText())
        uprojectData.EngineAssociation = engine.id
        uprojectData.DisableEnginePluginsByDefault = disableEnginePlugins
        val uprojectText = mapper.writeValueAsString(uprojectData)
        logger.debug("Content of final UProject: $uprojectText")
        uprojectFile.writeText(uprojectText)
    }

    protected fun generateSolutionFromUProject(uprojectFile: File, timeout: Duration = Duration.ofSeconds(90)) {
        val ue5specific = if (unrealInfo.currentEngine!!.version.major > 4) "UnrealBuildTool/" else ""
        val engineType = if (unrealInfo.currentEngine!!.isInstalledBuild) "-rocket" else "-engine"
        val ubtExecutable = "UnrealBuildTool${if (SystemInfo.isWindows) ".exe" else ""}"
        val ubtBuildTool =
            unrealInfo.currentEnginePath!!.resolve("Engine/Binaries/DotNET/${ue5specific}/${ubtExecutable}").absolutePath
        logger.info("Unreal Engine Build Tool Path: $ubtBuildTool")
        val ubtCommand = listOf(
            ubtBuildTool,
            "-ProjectFiles",
            "-game",
            "-progress",
            engineType,
            "-project=\"${uprojectFile.absolutePath}\""
        )

        ProcessBuilder(ubtCommand)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor(timeout.seconds, TimeUnit.SECONDS)
    }

    fun calculateRootPathInSolutionExplorer(
        projectName: String,
        openWith: EngineInfo.UnrealOpenType
    ): Array<String> {
        return mutableListOf(projectName).apply {
            if (openWith == EngineInfo.UnrealOpenType.Sln) add("Games")
            add(projectName)
        }.toTypedArray()
    }

    fun calculateProjectPathInSolutionExplorer(
        projectName: String,
        openWith: EngineInfo.UnrealOpenType
    ): Array<String> {
        return calculateRootPathInSolutionExplorer(projectName, openWith) + "Source" + projectName
    }

    val unrealPathsToMask: MutableMap<String, String>
        get() = mutableMapOf(
            Pair("absolute_ue_root", unrealInfo.currentEnginePath!!.toString()),
            Pair("relative_path_ue_root", unrealInfo.currentEngine!!.path.toIOFile().name)
        )
    val unrealRegexToMask: MutableMap<String, Regex>
        get() = mutableMapOf(
            Pair("number of projects", Regex("\\d?,?\\d{2,3} projects")),
            Pair("<masked>", Regex("FullIncludePath:;.*")),
            Pair("relative_path/", Regex("(\\.\\.[\\\\/])+"))
        )
    
    protected fun backupProject(backupFolder: File = File(tempDirectory).resolve("${activeSolution}_backup")): File {
        activeSolutionDirectory.copyRecursively(backupFolder)
        return backupFolder
    }

    protected fun restoreProject(projectDir: File, backupDir: File, profile: BackupRestoreProfile) {
        fun createProjectSnaphost(folder: File): MutableMap<String, String> {
            val snapshot: MutableMap<String, String> = mutableMapOf()
            folder.walk().onEnter { dir -> dir.name !in profile.ignoreDirs && !dir.name.startsWith(".") }
                .filter { it.isFile && !it.isHidden }.forEach { file ->
                    val relatedPath = file.path.drop(folder.path.length + 1)
                    val content = file.readText()

                    snapshot[relatedPath] = content
                }
            logger.info("Snapshot created: ${snapshot.keys.joinToString("\n")}")
            return snapshot
        }

        fun restoreProjectFromSnapshot(folder: File, snapshot: MutableMap<String, String>): Array<File> {
            val deletedFiles: MutableList<String> = mutableListOf()
            val replacedFiles: MutableList<String> = mutableListOf()
            val nonProcessedFiles: MutableList<String> = mutableListOf()

            val restoredFiles = mutableListOf<File>()

            folder.walk().onEnter { dir -> dir.name !in profile.ignoreDirs && !dir.name.startsWith(".") }
                .filter { it.isFile && !it.isHidden }.forEach { file ->
                    val relatedPath = file.path.drop(folder.path.length + 1)
                    val content = file.readText()

                    logger.info("Processing: $relatedPath")

                    val contentFromSnapshot: String? = snapshot[relatedPath]

                    if (contentFromSnapshot == null) {
                        deletedFiles.add(relatedPath)
                        restoredFiles.add(file)
                        file.delete()
                    } else if (contentFromSnapshot != content) {
                        replacedFiles.add(relatedPath)
                        restoredFiles.add(file)
                        file.writeText(contentFromSnapshot)
                    } else {
                        nonProcessedFiles.add(relatedPath)
                    }
                    snapshot.remove(relatedPath)
                }
            logger.info("Creating missing files")

            snapshot.forEach {
                logger.info(it.key)
                val creatingFile = folder.resolve(it.key)
                Files.createDirectories(creatingFile.parentFile.toPath())
                creatingFile.writeText(it.value)
                restoredFiles.add(creatingFile)
            }

            logger.info("\n\nMissing files:")
            snapshot.forEach { logger.info(it.key) }
            logger.info("=================")

            logger.info("Deleted files:")
            deletedFiles.forEach { logger.info(it) }
            logger.info("=================")

            logger.info("Replaced files:")
            replacedFiles.forEach { logger.info(it) }
            logger.info("=================")

            logger.info("NonProcessed files:")
            nonProcessedFiles.forEach { logger.info(it) }
            logger.info("=================")
            return restoredFiles.toTypedArray()
        }

        waitForDaemonCloseAllOpenEditors(project)

        changeFileSystem2(project) {
            restoreProjectFromSnapshot(projectDir, createProjectSnaphost(backupDir))
        }
    }

    class BackupRestoreProfile {
        val ignoreDirs: MutableList<String> = mutableListOf()
    }
    
    // ========== Data Provider section ==========
    /**
     * For some reasons TestNG cannot create instance of inner class, so this part lives in base class.
     * And we cannot use separate class for data provider 'cause we need [EngineInfo.testingEngines] from [UnrealBase] class.
     * The alternative is make a call to the backend from data provider, which is awful option.
     *
     * We have UE4/UE5 EGS/Source and Sln/Uproject project models
     * For different tests we need different intersections, so we use scheme like <engineType>_<projectModelType>
     * Examples: egsOnly_AllPModels, ue5EgsOnly_AllPModels, ue4Egs_slnOnly, egsOnly_uprojectOnly, AllEngines_slnOnly
     */

    @Suppress("FunctionName")
    @DataProvider
    fun AllEngines_AllPModels(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(allModels) { true }
    }

    @DataProvider
    fun AllEngines_slnOnly(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(onlySln) { true }
    }

    @DataProvider
    fun AllEngines_uprojectOnly(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(onlyUproject) { true }
    }

    @Suppress("FunctionName")
    @DataProvider
    fun egsOnly_AllPModels(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(allModels) { it.isInstalledBuild }
    }

    @Suppress("FunctionName")
    @DataProvider
    fun egsOnly_SlnOnly(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(onlySln) { it.isInstalledBuild }
    }

    @Suppress("FunctionName")
    @DataProvider
    fun ue5EgsOnly_AllPModels(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(allModels) { it.isInstalledBuild && it.version.major == 5 }
    }

    @DataProvider
    fun ue5SourceOnly_AllPModels(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(allModels) { !it.isInstalledBuild && it.version.major == 5 }
    }

    @DataProvider
    fun ue5SourceOnly_uprojectOnly(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(onlyUproject) { !it.isInstalledBuild && it.version.major == 5 }
    }

    @Suppress("FunctionName")
    @DataProvider
    fun ue5Only_slnOnly(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(onlySln) { it.version.major == 5 }
    }

    @DataProvider
    fun ue5EgsOnly_slnOnly(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(onlySln) { it.isInstalledBuild && it.version.major == 5 }
    }

    @DataProvider
    fun ue51Only_uprojectOnly(): MutableIterator<Array<Any>> {
        return generateUnrealDataProvider(onlyUproject) { it.isInstalledBuild && it.version.major == 5 && it.version.minor == 1 }
    }

    // ===== Private things for creating Data Providers above =====
    protected val guidRegex = "^[{]?[\\da-fA-F]{8}-([\\da-fA-F]{4}-){3}[\\da-fA-F]{12}[}]?$".toRegex()

    /**
     * Little hack for generate unique name in com.jetbrains.rider.test.TestCaseRunner#extractTestName
     *  based on file template type, [EngineInfo.UnrealOpenType], [UnrealEngine.version] and what engine uses - EGS/Source.
     * Unique name need for TestNG test collector, gold file/dir name, logging, etc.
     */
    protected open val uniqueDataString: (String, UnrealEngine) -> String =
        { baseString: String, engine: UnrealEngine ->
            // If we use engine from source, it's ID is GUID, so we replace it by 'normal' id plus ".fromSouce" string
            // else just replace dots in engine version, 'cause of part after last dot will be parsed as file type.
            if (engine.id.matches(guidRegex)) "$baseString${engine.version.major}_${engine.version.minor}fromSource"
            else "$baseString${engine.id.replace('.', '_')}"
        }

    protected open fun generateUnrealDataProvider(
        unrealPmTypes: Array<EngineInfo.UnrealOpenType>,
        predicate: (UnrealEngine) -> Boolean
    ): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        /**
         * [unrealInfo] initialized in [suiteSetup]. Right before data provider invocation
         */
        unrealInfo.testingEngines.filterEngines(predicate).forEach { engine ->
            unrealPmTypes.forEach { type ->
                result.add(arrayOf(uniqueDataString("$type", engine), type, engine))
            }
        }
        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }

    protected fun <T> Array<T>.filterEngines(predicate: (T) -> Boolean): List<T> {
        return this.filter(predicate).ifEmpty {
            throw Exception("Failed to filter engines in $this by $predicate")
        }
    }

    // Just for easy calling
    private val allModels = arrayOf(EngineInfo.UnrealOpenType.Sln, EngineInfo.UnrealOpenType.Uproject)
    private val onlySln = arrayOf(EngineInfo.UnrealOpenType.Sln)
    private val onlyUproject = arrayOf(EngineInfo.UnrealOpenType.Uproject)

    // ========== End of Data Provider section ==========
}