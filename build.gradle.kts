import com.jetbrains.plugin.structure.base.utils.isFile
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import org.jetbrains.intellij.platform.gradle.Constants

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS

plugins {
    id("me.filippov.gradle.jvm.wrapper")
    id("org.jetbrains.changelog") version "2.0.0"
    id("org.jetbrains.intellij.platform")
    kotlin("jvm")
}

repositories {
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-repository/snapshots")
    maven("https://cache-redirector.jetbrains.com/maven-central")
    intellijPlatform {
        defaultRepositories()
        jetbrainsRuntime()
    }
}

dependencies {
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.14.0")
}

apply {
    plugin("kotlin")
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
        kotlin.srcDir("src/rider/generated/kotlin")
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
        if (currentDir.resolve(".ultimate.root.marker").exists()) {
            return currentDir
        }
        currentDir = currentDir.parentFile
    }

    return null
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

dependencies {
    intellijPlatform {
        val dependencyPath = File(projectDir, "dependencies")
        if (dependencyPath.exists()) {
            val localPath = dependencyPath.canonicalPath
            local(localPath)
            logger.lifecycle("Will use ${File(localPath, "build.txt").readText()} from $localPath as RiderSDK")
        } else {
            val version = "${project.property("majorVersion")}-SNAPSHOT"
            logger.lifecycle("*** Using Rider SDK $version from intellij-snapshots repository")
            rider(version)
        }

        jetbrainsRuntime()

        instrumentationTools()

        // Workaround for https://youtrack.jetbrains.com/issue/IDEA-179607
        bundledPlugin("rider.intellij.plugin.appender")

        bundledPlugin("com.intellij.cidr.debugger")
        bundledPlugin("com.jetbrains.rider-cpp")
    }
}

intellijPlatform {
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

val riderModel: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(riderModel.name, provider {
        intellijPlatform.platformPath.resolve("lib/rd/rider-model.jar").also {
            check(it.isFile) {
                "rider-model.jar is not found at $riderModel"
            }
        }
    }) {
        builtBy(Constants.Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
    }
}

tasks {
    val dotNetSdkPath by lazy {
        val sdkPath = intellijPlatform.platformPath.resolve("lib/DotNetSdkForRdPlugins").absolute()
        assert(sdkPath.isDirectory())
        println(".NET SDK path: $sdkPath")

        return@lazy sdkPath.toRealPath()
    }

    withType<RunIdeTask>().configureEach {
        maxHeapSize = "4096m"
    }

    withType<Test>().configureEach {
        maxHeapSize = "4096m"
        if (project.hasProperty("ignoreFailures")) { ignoreFailures = true }
        useTestNG {
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

        inputs.property("dotNetSdkFile", { dotNetSdkPath.toString() })
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
        inputs.property("dotNetSdkFile", { dotNetSdkPath.toString() })
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
            from(it) { into("${intellijPlatform.projectName.get()}/dotnet") }
        }


        from(packCppSide.get().archiveFile) {
            into("${intellijPlatform.projectName.get()}/EditorPlugin")
        }

        doLast {
            dllFiles.forEach { file ->
                if (!file.exists()) throw RuntimeException("File $file does not exist")
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val generateModels by registering {
        group = "protocol"
        description = "Generates protocol models."
        dependsOn(":protocol:rdgen")
    }

    withType<Delete> {
        delete(csOutputRoot, cppOutputRoot, ktOutputRoot)
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

    wrapper {
        gradleVersion = "8.7"
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion}-bin.zip"
    }
}
