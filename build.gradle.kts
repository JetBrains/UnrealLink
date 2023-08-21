import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenTask
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS

buildscript {
    repositories {
        maven { setUrl("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2") }
        mavenLocal()
    }
    dependencies {
        classpath("com.jetbrains.rd:rd-gen:2023.3.2-preview2")
    }
}

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
}

plugins {
    kotlin("jvm") version "1.8.20"
    id("me.filippov.gradle.jvm.wrapper") version "0.14.0"
    id("org.jetbrains.changelog") version "2.0.0"
    id("org.jetbrains.intellij") version "1.13.3"
    id("io.qameta.allure") version "2.11.2"
}

dependencies {
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.14.0")
}

apply {
    plugin("kotlin")
    plugin("com.jetbrains.rdgen")
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("src/rider/main/kotlin")
        }
        test {
            kotlin.srcDir("src/rider/test/kotlin")
        }
    }
}

sourceSets {
    main {
        resources.srcDir("src/rider/main/resources")
    }
}

project.version = "${property("majorVersion")}." +
        "${property("minorVersion")}." +
        "${property("buildCounter")}"

if (System.getenv("TEAMCITY_VERSION") != null) {
    logger.lifecycle("##teamcity[buildNumber '${project.version}']")
} else {
    logger.lifecycle("Plugin version: ${project.version}")
}

val buildConfigurationProp = project.property("buildConfiguration").toString()

val repoRoot by extra { project.rootDir }
val isWindows by extra { Os.isFamily(Os.FAMILY_WINDOWS) }
val idePluginId by extra { "RiderPlugin" }
val dotNetSolutionId by extra { "UnrealLink" }
val dotNetDir by extra { File(repoRoot, "src/dotnet") }
val dotNetBinDir by extra { dotNetDir.resolve("$idePluginId.$dotNetSolutionId").resolve("bin") }
val dotNetPluginId by extra { "$idePluginId.${project.name}" }
val dotNetSolution by extra { File(repoRoot, "$dotNetSolutionId.sln") }
val modelDir = File(repoRoot, "protocol/src/main/kotlin/model")
val hashBaseDir = File(repoRoot, "build/rdgen")
val ktOutputRelativePath = "src/rider/main/kotlin/com/jetbrains/rider/model"
val cppOutputRoot = File(repoRoot, "src/cpp/RiderLink/Source/RiderLink/Public/Model")
val csOutputRoot = File(repoRoot, "src/dotnet/RiderPlugin.UnrealLink/obj/model")
val ktOutputRoot = File(repoRoot, ktOutputRelativePath)
val riderLinkDir = File("$rootDir/src/cpp/RiderLink")
val rdLibDirectory: () -> File = { file("${tasks.setupDependencies.get().idea.get().classes}/lib/rd") }
extra["rdLibDirectory"] = rdLibDirectory

val productMonorepoDir = getProductMonorepoRoot()
val monorepoPreGeneratedRootDir by lazy { productMonorepoDir?.resolve("Plugins/_UnrealLink.Pregenerated") ?: error("Building not in monorepo") }
val monorepoPreGeneratedFrontendDir by lazy {  monorepoPreGeneratedRootDir.resolve("Frontend") }
val monorepoPreGeneratedBackendDir by lazy {  monorepoPreGeneratedRootDir.resolve("BackendModel") }
val monorepoPreGeneratedCppDir by lazy {  monorepoPreGeneratedRootDir.resolve("CppModel") }
val ktOutputMonorepoRoot by lazy { monorepoPreGeneratedFrontendDir.resolve(ktOutputRelativePath) }

extra["productMonorepoDir"] = productMonorepoDir
extra["monorepoPreGeneratedFrontendDir"] = productMonorepoDir

val currentBranchName = getBranchName()

fun TaskContainerScope.setupCleanup(task: Task) {
    withType<Delete> {
        delete(task.outputs.files)
    }
}

fun getBranchName(): String {
    val stdOut = ByteArrayOutputStream()
    val result = project.exec {
        executable = "git"
        args = listOf("rev-parse", "--abbrev-ref", "HEAD")
        workingDir = projectDir
        standardOutput = stdOut
    }
    if (result.exitValue == 0) {
        val output = stdOut.toString().trim()
        if (output.isNotEmpty())
            return output
    }
    return "net222"
}

fun getProductMonorepoRoot(): File? {
    var currentDir = repoRoot

    while (currentDir.parent != null) {
        if (currentDir.listFiles()?.any { it.name == ".vcs" } == true) {
            return currentDir
        }
        currentDir = currentDir.parentFile
    }

    return null
}

// Add an error on execution if it is not a monorepo.
//  We check it because on configuration stage we should not configure tasks for a monorepo but want to throw an error.
fun Task.checkIfMonorepoOrAddThrowingOnExecution(): Boolean {
    if (productMonorepoDir == null) {
        doFirst {
            throw GradleException("Building not in monorepo")
        }
        return false
    }

    return true
}


changelog {
    version.set(project.version.toString())
    // https://github.com/JetBrains/gradle-changelog-plugin/blob/main/src/main/kotlin/org/jetbrains/changelog/Changelog.kt#L23
    // This is just common semVerRegex with the addition of a forth optional group (number) ( x.x.x[.x][-alpha43] )
    headerParserRegex.set(
        """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\.?(0|[1-9]\d*)?(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)
            (?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?${'$'}"""
            .trimMargin().toRegex())
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Known Issues"))
    keepUnreleasedSection.set(true)
    itemPrefix.set("-")
}

intellij {
    type.set("RD")
    instrumentCode.set(false)
    downloadSources.set(false)

    plugins.set(listOf("com.jetbrains.rider-cpp"))

    val dependencyPath = File(projectDir, "dependencies")
    if (dependencyPath.exists()) {
        localPath.set(dependencyPath.canonicalPath)
        println("Will use ${File(localPath.get(), "build.txt").readText()} from ${localPath.get()} as RiderSDK")
    } else {
        version.set("${project.property("majorVersion")}-SNAPSHOT")
        if (productMonorepoDir == null) {
            println("Will download and use build/riderRD-${version.get()} as RiderSDK")
        }
    }

    tasks {
        val currentReleaseNotesAsHtml = """
            <body>
            <p><b>New in "${project.version}"</b></p>
            <p>${changelog.getLatest().toHTML()}</p>
            <p>See the <a href="https://github.com/JetBrains/UnrealLink/blob/$currentBranchName/CHANGELOG.md">CHANGELOG</a> for more details and history.</p>
            </body>
        """.trimIndent()

        val currentReleaseNotesAsMarkdown = """
            ## New in ${project.version}
            ${changelog.getLatest().toText()}
            See the [CHANGELOG](https://github.com/JetBrains/UnrealLink/blob/$currentBranchName/CHANGELOG.md) for more details and history.
        """.trimIndent()
        val dumpCurrentChangelog by registering {
            val outputFile = File("${project.buildDir}/release_notes.md")
            outputs.file(outputFile)
            doLast { outputFile.writeText(currentReleaseNotesAsMarkdown) }
        }

        // PatchPluginXml gets latest (always Unreleased) section from current changelog and write it into plugin.xml
        // dumpCurrentChangelog dumps the same section to file (for Marketplace changelog)
        // After, patchChangelog rename [Unreleased] to [202x.x.x.x] and create new empty Unreleased.
        // So order is important!
        patchPluginXml { changeNotes.set( provider { currentReleaseNotesAsHtml }) }
        patchChangelog { mustRunAfter(patchPluginXml, dumpCurrentChangelog) }

        publishPlugin {
            dependsOn(patchPluginXml, dumpCurrentChangelog, patchChangelog)
            token.set(System.getenv("UNREALLINK_intellijPublishToken"))

            val pubChannels = project.findProperty("publishChannels")
            if ( pubChannels != null) {
                val chan = pubChannels.toString().split(',')
                println("Channels for publish $chan")
                channels.set(chan)
            } else {
                channels.set(listOf("alpha"))
            }
        }
    }
}

tasks {
    val dotNetSdkPath by lazy {
        val sdkPath = setupDependencies.get().idea.get().classes.resolve("lib").resolve("DotNetSdkForRdPlugins")
        assert(sdkPath.isDirectory)
        println(".NET SDK path: $sdkPath")

        return@lazy sdkPath.canonicalPath
    }

    val riderModelJar by lazy {
        val rdLib = setupDependencies.get().idea.get().classes.resolve("lib").resolve("rd")
        assert(rdLib.isDirectory)
        val jarFile = File(rdLib, "rider-model.jar")
        assert(jarFile.isFile)
        return@lazy jarFile.canonicalPath
    }

    withType<RunIdeTask>().configureEach {
        maxHeapSize = "4096m"
    }

    withType<Test>().configureEach {
        maxHeapSize = "4096m"
        if (project.hasProperty("ignoreFailures")) { ignoreFailures = true }
        useTestNG {
            listeners.add("com.jetbrains.rider.test.allure.AllureListener")
        }
        testLogging {
            showStandardStreams = true
            showExceptions = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    withType<KotlinCompile>().configureEach {
        dependsOn("generateModels")
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    val prepareRiderBuildProps by registering {
        group = "RiderBackend"
        val generatedFile = project.buildDir.resolve("DotNetSdkPath.generated.props")

        inputs.property("dotNetSdkFile", { dotNetSdkPath })
        outputs.file(generatedFile)

        doLast {
            project.file(generatedFile).writeText(
                """<Project>
            |  <PropertyGroup>
            |    <DotNetSdkPath>$dotNetSdkPath</DotNetSdkPath>
            |  </PropertyGroup>
            |</Project>""".trimMargin()
            )
        }
    }

    val prepareNuGetConfig by registering {
        group = "RiderBackend"
        dependsOn(prepareRiderBuildProps)

        val generatedFile = project.projectDir.resolve("NuGet.Config")
        inputs.property("dotNetSdkFile", { dotNetSdkPath })
        outputs.file(generatedFile)
        doLast {
            val dotNetSdkFile = dotNetSdkPath
            logger.info("dotNetSdk location: '$dotNetSdkFile'")

            val nugetConfigText = """<?xml version="1.0" encoding="utf-8"?>
        |<configuration>
        |  <packageSources>
        |    <clear />
        |    <add key="local-dotnet-sdk" value="$dotNetSdkFile" />
        |    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
        |  </packageSources>
        |</configuration>
        """.trimMargin()
            generatedFile.writeText(nugetConfigText)

            logger.info("Generated content:\n$nugetConfigText")
        }
    }

    val buildResharperHost by registering {
        group = "RiderBackend"
        description = "Build backend for Rider"
        dependsOn(":generateModels", prepareNuGetConfig)

        inputs.file(file(dotNetSolution))
        inputs.dir(file("$repoRoot/src/dotnet"))
        outputs.dir(file("$repoRoot/src/dotnet/RiderPlugin.UnrealLink/bin/RiderPlugin.UnrealLink/$buildConfigurationProp"))

        doLast {
            val warningsAsErrors: String by project.extra
            val buildArguments = listOf(
                "build",
                dotNetSolution.canonicalPath,
                "/p:Configuration=$buildConfigurationProp",
                "/p:Version=${project.version}",
                "/p:TreatWarningsAsErrors=$warningsAsErrors",
                "/v:${project.properties.getOrDefault("dotnetVerbosity", "minimal")}",
                "/bl:${dotNetSolution.name}.binlog",
                "/nologo"
            )
            logger.info("call dotnet.cmd with '{}'", buildArguments)
            project.exec {
                executable = "$rootDir/tools/dotnet.cmd"
                args = buildArguments
                workingDir = dotNetSolution.parentFile
            }
        }
    }

    val patchUpluginVersion by register("patchUpluginVersion") {
        val pathToUpluginTemplate = File("${project.rootDir}/src/cpp/RiderLink/RiderLink.uplugin.template")
        val filePathToUplugin = File("${project.rootDir}/src/cpp/RiderLink/RiderLink.uplugin")
        inputs.file(pathToUpluginTemplate)
        inputs.property("version", project.version)
        outputs.file(filePathToUplugin)
        doLast {
            if(filePathToUplugin.exists())
                filePathToUplugin.delete()

            pathToUpluginTemplate.copyTo(filePathToUplugin)

            val text = filePathToUplugin.readLines().map {
                it.replace("%PLUGIN_VERSION%", "${project.version}")
            }
            filePathToUplugin.writeText(text.joinToString(System.lineSeparator()))
        }
    }
    withType<Delete> {
        delete(patchUpluginVersion.outputs.files)
    }

    val generateChecksum by register("generateChecksum") {
        dependsOn(":generateModels")
        val upluginFile = riderLinkDir.resolve("RiderLink.uplugin.template")
        val resourcesDir = riderLinkDir.resolve("Resources")
        val sourceDir = riderLinkDir.resolve("Source")
        val checksumFile = riderLinkDir.resolve("Resources/checksum")
        inputs.file(upluginFile)
        inputs.dir(resourcesDir)
        inputs.dir(sourceDir)
        outputs.file(checksumFile)
        doLast {
            checksumFile.delete()
            val inputFiles = sequence{
                yield(upluginFile)
                resourcesDir.walkTopDown().forEach { if(it.isFile && (it.nameWithoutExtension != "checksum")) yield(it) }
                sourceDir.walkTopDown().forEach { if(it.isFile) yield(it) }
            }
            val instance = MessageDigest.getInstance("MD5")
            inputFiles.forEach { instance.update(it.readBytes()) }
            checksumFile.writeBytes(instance.digest())
        }
    }
    withType<Delete> {
        delete(generateChecksum.outputs.files)
    }

    val packCppSide by registering(Zip::class) {
        dependsOn(patchUpluginVersion)
        dependsOn(":generateModels")
        dependsOn(generateChecksum)

        archiveFileName.set("RiderLink.zip")
        excludes.addAll(arrayOf("RiderLink.uplugin.template", "Intermediate", "Binaries"))
        destinationDirectory.set(File("$rootDir/build/distributions"))
        from("$rootDir/src/cpp/RiderLink")
    }

    withType<PrepareSandboxTask> {
        dependsOn(buildResharperHost, packCppSide)

        outputs.upToDateWhen { false } //need to dotnet artifacts be included when only dotnet sources were changed

        val outputFolder = dotNetBinDir
            .resolve(dotNetPluginId)
            .resolve(buildConfigurationProp)

        val dllFiles = listOf(
            File(outputFolder, "$dotNetPluginId.dll"),
            File(outputFolder, "$dotNetPluginId.pdb")
        )

        dllFiles.forEach {
            from(it) { into("${intellij.pluginName.get()}/dotnet") }
        }


        from(packCppSide.get().archiveFile) {
            into("${intellij.pluginName.get()}/EditorPlugin")
        }

        doLast {
            dllFiles.forEach { file ->
                if (!file.exists()) throw RuntimeException("File $file does not exist")
            }
        }
    }

    fun generateUE4Lib(monorepo: Boolean) = register<RdGenTask>("generateUE4Lib" + if(monorepo) "Monorepo" else "") {
        if (monorepo && !checkIfMonorepoOrAddThrowingOnExecution()) return@register

        val csLibraryOutput =
            if (monorepo) File(monorepoPreGeneratedBackendDir, "Library")
            else File(csOutputRoot, "Library")
        val cppLibraryOutput =
            if (monorepo) File(monorepoPreGeneratedCppDir, "Library")
            else File(cppOutputRoot, "Library")
        val ktLibraryOutput =
            if (monorepo) File(ktOutputMonorepoRoot, "Library")
            else File(ktOutputRoot, "Library")

        inputs.dir(modelDir.resolve("lib").resolve("ue4"))
        outputs.dirs(
            csLibraryOutput, cppLibraryOutput, ktLibraryOutput
        )

        configure<RdGenExtension> {
            verbose =
                project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG

            // *** Classpath and sources ***
            if (monorepo) {
                sources(
                    listOf(
                        File("$productMonorepoDir/Rider/Frontend/rider/model/sources"),
                        File("$productMonorepoDir/Rider/ultimate/remote-dev/rd-ide-model-sources"),
                        modelDir.resolve("lib/ue4")
                    )
                )
            }
            else {
                classpath({riderModelJar})
                sources("$modelDir/lib/ue4")
            }

            hashFolder = "$hashBaseDir/lib/ue4"
            packages = "model.lib.ue4"
            generator {
                language = "csharp"
                transform = "symmetric"
                root = "model.lib.ue4.UE4Library"
                directory = "$csLibraryOutput"
                if (monorepo) generatedFileSuffix = ".Pregenerated"
            }

            generator {
                language = "cpp"
                transform = "reversed"
                root = "model.lib.ue4.UE4Library"
                directory = "$cppLibraryOutput"
                if (monorepo) generatedFileSuffix = ".Pregenerated"
            }

            generator {
                language = "kotlin"
                transform = "asis"
                root = "model.lib.ue4.UE4Library"
                directory = "$ktLibraryOutput"
                if (monorepo) generatedFileSuffix = ".Pregenerated"
            }
        }
    }

    val generateUE4Lib by generateUE4Lib(false)
    val generateUE4LibMonorepo by generateUE4Lib(true)

    withType<Delete> {
        delete(generateUE4Lib.outputs.files)
    }

    fun generateRiderModel(monorepo: Boolean) = register<RdGenTask>("generateRiderModel" + if(monorepo) "Monorepo" else "") {
        if (monorepo && !checkIfMonorepoOrAddThrowingOnExecution()) return@register

        if (monorepo) dependsOn(generateUE4LibMonorepo)
        else dependsOn(generateUE4Lib)

        val csRiderOutput =
            if (monorepo) File(monorepoPreGeneratedBackendDir, "RdRiderProtocol")
            else File(csOutputRoot, "RdRiderProtocol")
        val ktRiderOutput =
            if (monorepo) File(ktOutputMonorepoRoot, "RdRiderProtocol")
            else File(ktOutputRoot, "RdRiderProtocol")

        inputs.dir(modelDir.resolve("rider"))
        outputs.dirs(csRiderOutput, ktRiderOutput)

        configure<RdGenExtension> {
            verbose = project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG

            // *** Classpath and sources ***
            if (monorepo) {
                sources(
                    listOf(
                        File("$productMonorepoDir/Rider/Frontend/rider/model/sources"),
                        File("$productMonorepoDir/Rider/ultimate/remote-dev/rd-ide-model-sources"),
                        modelDir
                    )
                )
            }
            else {
                classpath({riderModelJar})
                sources("$modelDir")
            }

            packages = "model.rider"
            hashFolder = "$hashBaseDir/rider"

            generator {
                language = "kotlin"
                transform = "asis"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                directory = "$ktRiderOutput"
                if (monorepo) generatedFileSuffix = ".Pregenerated"
            }

            generator {
                language = "csharp"
                transform = "reversed"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                directory = "$csRiderOutput"
                if (monorepo) generatedFileSuffix = ".Pregenerated"
            }
        }
    }

    val generateRiderModel by generateRiderModel(false)
    val generateRiderModelMonorepo by generateRiderModel(true)

    withType<Delete> {
        delete(generateRiderModel.outputs.files)
    }

    fun generateEditorPluginModel(monorepo: Boolean) = register<RdGenTask>("generateEditorPluginModel" + if (monorepo) "Monorepo" else "") {
        if (monorepo && !checkIfMonorepoOrAddThrowingOnExecution()) return@register

        if (monorepo) dependsOn(generateUE4LibMonorepo)
        else dependsOn(generateUE4Lib)

        val csEditorOutput =
            if (monorepo) File(monorepoPreGeneratedBackendDir, "RdEditorProtocol")
            else File(csOutputRoot, "RdEditorProtocol")
        val cppEditorOutput =
            if (monorepo) File(monorepoPreGeneratedCppDir, "RdEditorProtocol")
            else File(cppOutputRoot, "RdEditorProtocol")

        inputs.dir(modelDir.resolve("editorPlugin"))
        outputs.dirs(
            csEditorOutput, cppEditorOutput
        )

        configure<RdGenExtension> {
            verbose =
                project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG
            println()

            // *** Classpath and sources ***
            if (monorepo) {
                classpath({
                    val riderModelClassPathFile: String by project
                    File(riderModelClassPathFile).readLines()
                })
            }
            else {
                classpath({riderModelJar})
            }
            sources(modelDir)

            hashFolder = "$hashBaseDir/editorPlugin"
            packages = "model.editorPlugin"

            generator {
                language = "csharp"
                transform = "asis"
                root = "model.editorPlugin.RdEditorRoot"
                directory = "$csEditorOutput"
                if (monorepo) generatedFileSuffix = ".Pregenerated"
            }

            generator {
                language = "cpp"
                transform = "reversed"
                root = "model.editorPlugin.RdEditorRoot"
                directory = "$cppEditorOutput"
                if (monorepo) generatedFileSuffix = ".Pregenerated"
            }
        }
    }

    val generateEditorPluginModel by generateEditorPluginModel(false)
    val generateEditorPluginModelMonorepo by generateEditorPluginModel(true)

    withType<Delete> {
        delete(generateEditorPluginModel.outputs.files)
    }

    @Suppress("UNUSED_VARIABLE")
    val generateModels by registering {
        group = "protocol"
        description = "Generates protocol models."
        dependsOn(generateEditorPluginModel)
        dependsOn(generateRiderModel)
    }
    withType<Delete> {
        delete(csOutputRoot, cppOutputRoot, ktOutputRoot)
    }

    register("generateModelsMonorepo") {
        dependsOn(generateEditorPluginModelMonorepo)
        dependsOn(generateRiderModelMonorepo)
    }

    val getUnrealEngineProject by register("getUnrealEngineProject") {
        doLast {
            val ueProjectPathTxt = rootDir.resolve("UnrealEngineProjectPath.txt")
            if (ueProjectPathTxt.exists()) {
                val ueProjectPath = ueProjectPathTxt.readText()
                val ueProjectPathDir = File(ueProjectPath)
                if (!ueProjectPathDir.exists()) throw AssertionError("$ueProjectPathDir doesn't exist")
                if (!ueProjectPathDir.isDirectory) throw AssertionError("$ueProjectPathDir is not directory")

                val isUEProject = ueProjectPathDir.listFiles()?.any {
                    it.extension == "uproject"
                }
                if (isUEProject == true) {
                    extra["UnrealProjectPath"] = ueProjectPathDir
                } else {
                    throw AssertionError("Add path to a valid UnrealEngine project folder to: $ueProjectPathTxt")
                }
            } else {
                ueProjectPathTxt.createNewFile()
                throw AssertionError("Add path to a valid UnrealEngine project folder to: $ueProjectPathTxt")
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val symlinkPluginToUnrealProject by registering {
        dependsOn(getUnrealEngineProject)
        dependsOn(patchUpluginVersion)
        doLast {
            val unrealProjectPath = getUnrealEngineProject.extra["UnrealProjectPath"] as File
            val targetDir = File("$unrealProjectPath/Plugins/Developer/RiderLink")

            if(targetDir.exists()) {
                val stdOut = ByteArrayOutputStream()
                // Check if it's Junction
                val result = exec {
                    commandLine = if(isWindows)
                        listOf("cmd.exe", "/c", "fsutil", "reparsepoint", "query", targetDir.absolutePath, "|", "find", "Print Name:")
                    else
                        listOf("find", targetDir.absolutePath, "-maxdepth", "1", "-type", "l", "-ls")

                    isIgnoreExitValue = true
                    standardOutput = stdOut
                }

                // Check if it's Junction to local RiderLink
                if(result.exitValue == 0) {
                    val output = stdOut.toString().trim()
                    if (output.isNotEmpty()) {
                        val pathToJunction = if (isWindows)
                            output.substringAfter("Print Name:").trim()
                        else
                            output.substringAfter("->").trim()
                        if (File(pathToJunction) == riderLinkDir) {
                            println("Junction is already correct")
                            throw StopExecutionException()
                        }
                    }
                }

                // If it's not Junction or if it's a Junction but doesn't point to local RiderLink - delete it
                targetDir.delete()
            }

            targetDir.parentFile.mkdirs()
            val stdOut = ByteArrayOutputStream()
            val result = exec {
                commandLine = if(isWindows)
                    listOf("cmd.exe", "/c", "mklink", "/J", targetDir.absolutePath, riderLinkDir.absolutePath)
                else
                    listOf("ln", "-s", riderLinkDir.absolutePath, targetDir.absolutePath)
                errorOutput = stdOut
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                println(stdOut.toString().trim())
            }
        }
    }
}
