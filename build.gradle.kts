import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import com.jetbrains.rd.generator.gradle.RdGenTask
import com.jetbrains.rd.generator.gradle.RdGenExtension
import org.gradle.kotlin.dsl.support.listFilesOrdered
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

buildscript {
    repositories {
        maven { setUrl("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2")}
    }
    dependencies {
        classpath("com.jetbrains.rd:rd-gen:2021.3.4")
    }
}

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS

plugins {
    kotlin("jvm") version "1.4.32"
    id("org.jetbrains.changelog") version "1.3.1"
    id("org.jetbrains.intellij") version "1.2.0"
    id("com.jetbrains.rdgen") version "2021.3.4"
}

apply {
    plugin("kotlin")
    plugin("com.jetbrains.rdgen")
}

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
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
val cppOutputRoot = File(repoRoot, "src/cpp/RiderLink/Source/RiderLink/Public/Model")
val csOutputRoot = File(repoRoot, "src/dotnet/RiderPlugin.UnrealLink/obj/model")
val ktOutputRoot = File(repoRoot, "src/rider/main/kotlin/com/jetbrains/rider/model")
val riderLinkDir = File("$rootDir/src/cpp/RiderLink")

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
    return "net212"
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
        println("Will use ${File(localPath.get(), "build.txt").readText()} from $localPath as RiderSDK")
    } else {
        version.set("${project.property("majorVersion")}-SNAPSHOT")
        println("Will download and use build/riderRD-$version as RiderSDK")
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
        val sdkPath = intellij.ideaDependency.get().classes.resolve("lib").resolve("DotNetSdkForRdPlugins")
        assert(sdkPath.isDirectory)
        println(".NET SDK path: $sdkPath")

        return@lazy sdkPath
    }
    val rdLibDirectory by lazy {
        val intellij = rootProject.extensions.findByType(org.jetbrains.intellij.IntelliJPluginExtension::class.java)!!
        val rdLib = intellij.ideaDependency.get().classes.resolve("lib").resolve("rd")
        assert(rdLib.isDirectory)
        return@lazy rdLib
    }
    val riderModelJar by lazy<File> {
        val jarFile = File(rdLibDirectory, "rider-model.jar").canonicalFile
        assert(jarFile.isFile)
        return@lazy jarFile
    }

    withType<RunIdeTask> {
        maxHeapSize = "4096m"
    }

    withType<Test> {
        useTestNG()
        testLogging {
            showStandardStreams = true
            showExceptions = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    withType<KotlinCompile> {
        dependsOn("generateModels")
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    val prepareRiderBuildProps by registering {
        group = "RiderBackend"
        val generatedFile = project.buildDir.resolve("DotNetSdkPath.generated.props")

        inputs.property("dotNetSdkFile", { dotNetSdkPath.canonicalPath })
        outputs.file(generatedFile)

        doLast {
            project.file(generatedFile).writeText(
                """<Project>
            |  <PropertyGroup>
            |    <DotNetSdkPath>${dotNetSdkPath.canonicalPath}</DotNetSdkPath>
            |  </PropertyGroup>
            |</Project>""".trimMargin()
            )
        }
    }

    val prepareNuGetConfig by registering {
        group = "RiderBackend"
        dependsOn(prepareRiderBuildProps)

        val generatedFile = project.projectDir.resolve("NuGet.Config")
        inputs.property("dotNetSdkFile", { dotNetSdkPath.canonicalPath })
        outputs.file(generatedFile)
        doLast {
            val dotNetSdkFile = dotNetSdkPath
            logger.info("dotNetSdk location: '$dotNetSdkFile'")
            assert(dotNetSdkFile.isDirectory)

            val nugetConfigText = """<?xml version="1.0" encoding="utf-8"?>
        |<configuration>
        |  <packageSources>
        |    <clear />
        |    <add key="local-dotnet-sdk" value="${dotNetSdkFile.canonicalPath}" />
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
            logger.info("call dotnet.cmd with '$buildArguments'")
            project.exec {
                executable = "$rootDir/tools/dotnet.cmd"
                args = buildArguments
                workingDir = dotNetSolution.parentFile
            }
        }
    }

    val patchUpluginVersion by creating {
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

    val buildZipper by creating {
        description = "Build Zipper utility to pack RiderLink"

        val zipperSolution = File("$rootDir/tools/Zipper/Zipper.sln")
        inputs.file("$rootDir/tools/Zipper/Program.cs")
        inputs.file("$rootDir/tools/Zipper/Zipper.csproj")
        inputs.file(zipperSolution)
        val zipperFolder = File("$rootDir/tools/Zipper/bin/Release/net461")
        val zipperBinary = zipperFolder.resolve("Zipper.exe")
        outputs.file(zipperBinary)

        doLast {
            val buildArguments = listOf(
                "build",
                zipperSolution.absolutePath,
                "/p:Configuration=Release",
                "/nologo"
            )

            logger.info("call dotnet.cmd with '$buildArguments'")
            project.exec {
                executable = "$rootDir/tools/dotnet.cmd"
                args = buildArguments
                workingDir = zipperSolution.parentFile
            }
        }
    }

    val generateChecksum by creating {
        dependsOn(patchUpluginVersion)
        dependsOn(":generateModels")
        val upluginFile = riderLinkDir.resolve("RiderLink.uplugin.template")
        val resourcesDir = riderLinkDir.resolve("Resources")
        val sourceDir = riderLinkDir.resolve("Source")
        val checksumFile = riderLinkDir.resolve("checksum")
        inputs.file(upluginFile)
        inputs.dir(resourcesDir)
        inputs.dir(sourceDir)
        outputs.file(checksumFile)
        doLast {
            val inputFiles = sequence{
                yield(upluginFile)
                resourcesDir.listFilesOrdered().forEach { if(it.isFile) yield(it) }
                sourceDir.listFilesOrdered().forEach { if(it.isFile) yield(it) }
            }
            val instance = MessageDigest.getInstance("MD5")
            inputFiles.forEach { instance.update(it.readBytes()) }
            checksumFile.writeBytes(instance.digest())
        }
    }
    withType<Delete> {
        delete(generateChecksum.outputs.files)
    }

    val packCppSide by creating {
        dependsOn(patchUpluginVersion)
        dependsOn(":generateModels")
        dependsOn(generateChecksum)
        dependsOn(buildZipper)

        inputs.dir("$rootDir/src/cpp/RiderLink")
        val outputZip = File("$rootDir/build/distributions/RiderLink.zip")
        outputs.file(outputZip)
        doLast {
            if(isWindows){
                project.exec {
                    executable = buildZipper.outputs.files.first().absolutePath
                    args = listOf(riderLinkDir.absolutePath, outputZip.absolutePath)
                    workingDir = rootDir
                }
            } else {
                project.exec {
                    executable = "zsh"
                    args = listOf("-c", "eval",  "`/usr/libexec/path_helper -s`", "&&", "mono", buildZipper.outputs.files.first().absolutePath, riderLinkDir.absolutePath, outputZip.absolutePath)
                }
            }
        }
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
            from(it) { into("${intellij.pluginName}/dotnet") }
        }

        from(packCppSide.outputs.files.first()) {
            into("${intellij.pluginName}/EditorPlugin")
        }

        doLast {
            dllFiles.forEach { file ->
                if (!file.exists()) throw RuntimeException("File $file does not exist")
            }
        }
    }

    val generateUE4Lib by creating(RdGenTask::class) {
        val csLibraryOutput = File(csOutputRoot, "Library")
        val cppLibraryOutput = File(cppOutputRoot, "Library")
        val ktLibraryOutput = File(ktOutputRoot, "Library")

        inputs.dir(modelDir.resolve("lib").resolve("ue4"))
        outputs.dirs(
            csLibraryOutput
            ,cppLibraryOutput
            ,ktLibraryOutput
        )

        configure<RdGenExtension> {
            verbose =
                project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG
            classpath(riderModelJar)
            sources("$modelDir/lib/ue4")
            hashFolder = "$hashBaseDir/lib/ue4"
            packages = "model.lib.ue4"
            generator {
                language = "csharp"
                transform = "symmetric"
                root = "model.lib.ue4.UE4Library"
                directory = "$csLibraryOutput"
            }

            generator {
                language = "cpp"
                transform = "reversed"
                root = "model.lib.ue4.UE4Library"
                directory = "$cppLibraryOutput"
            }

            generator {
                language = "kotlin"
                transform = "asis"
                root = "model.lib.ue4.UE4Library"
                directory = "$ktLibraryOutput"
            }
        }
    }

    withType<Delete> {
        delete(generateUE4Lib.outputs.files)
    }

    val generateRiderModel by creating(RdGenTask::class) {
        dependsOn(generateUE4Lib)

        val csRiderOutput = File(csOutputRoot, "RdRiderProtocol")
        val ktRiderOutput = File(ktOutputRoot, "RdRiderProtocol")

        inputs.dir(modelDir.resolve("rider"))
        outputs.dirs(csRiderOutput, ktRiderOutput)

        configure<RdGenExtension> {
            // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
            // intellij SDK, which is extracted in afterEvaluate
            verbose = project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG
            classpath(riderModelJar)

            sources("$modelDir")
            packages = "model.rider"
            hashFolder = "$hashBaseDir/rider"

            generator {
                language = "kotlin"
                transform = "asis"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                directory = "$ktRiderOutput"

            }

            generator {
                language = "csharp"
                transform = "reversed"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                directory = "$csRiderOutput"
            }
        }
    }

    withType<Delete> {
        delete(generateRiderModel.outputs.files)
    }

    val generateEditorPluginModel by creating(RdGenTask::class) {
        dependsOn(generateUE4Lib)

        val csEditorOutput = File(csOutputRoot, "RdEditorProtocol")
        val cppEditorOutput = File(cppOutputRoot, "RdEditorProtocol")
        inputs.dir(modelDir.resolve("editorPlugin"))
        outputs.dirs(
            csEditorOutput
            ,cppEditorOutput
        )

        configure<RdGenExtension> {
            verbose =
                project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG
            println()
            classpath(riderModelJar)

            sources("$modelDir")
            hashFolder = "$hashBaseDir/editorPlugin"
            packages = "model.editorPlugin"

            generator {
                language = "csharp"
                transform = "asis"
                root = "model.editorPlugin.RdEditorRoot"
                directory = "$csEditorOutput"
            }

            generator {
                language = "cpp"
                transform = "reversed"
                root = "model.editorPlugin.RdEditorRoot"
                directory = "$cppEditorOutput"
            }
        }
    }

    withType<Delete> {
        delete(generateEditorPluginModel.outputs.files)
    }

    @Suppress("UNUSED_VARIABLE")
    val generateModels by creating {
        group = "protocol"
        description = "Generates protocol models."
        dependsOn(generateEditorPluginModel)
        dependsOn(generateRiderModel)
    }
    withType<Delete> {
        delete(csOutputRoot, cppOutputRoot, ktOutputRoot)
    }

    val getUnrealEngineProject by creating {
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
    val symlinkPluginToUnrealProject by creating {
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
                    if(output.isNotEmpty())
                    {
                        val pathToJunction = if(isWindows)
                            output.substringAfter("Print Name:").trim()
                        else
                            output.substringAfter("->").trim()
                        if(File(pathToJunction) == riderLinkDir) {
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

