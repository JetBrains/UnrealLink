import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.changelog.closure
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.ByteArrayOutputStream

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS

plugins {
    kotlin("jvm") version "1.4.32"

    id("org.jetbrains.changelog") version "1.1.2"
    id("org.jetbrains.intellij") version "0.7.2"
    id("com.jetbrains.rdgen") version "0.211.234"
}

dependencies {
    // only for suppress warning lib\kotlin-stdlib-jdk8.jar: Library has Kotlin runtime bundled into it
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = "1.4.32")
}

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
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

apply(from = "cpp.gradle.kts")

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
val dotnetSolution by extra { File(repoRoot, "$dotNetSolutionId.sln") }
val dotNetSdkPath by lazy {
    val sdkPath = intellij.ideaDependency.classes.resolve("lib").resolve("DotNetSdkForRdPlugins")
    assert(sdkPath.isDirectory)
    println(".NETSDK path: $sdkPath")

    return@lazy sdkPath
}

fun findDotNetCliPath(): String? {
    if (project.extra.has("dotNetCliPath")) {
        val dotNetCliPath = project.extra["dotNetCliPath"] as String
        logger.info("dotNetCliPath (cached): $dotNetCliPath")
        return dotNetCliPath
    }

    val pathComponents = System.getenv("PATH").split(File.pathSeparatorChar)
    for (dir in pathComponents) {
        val dotNetCliFile = File(dir, if (isWindows) "dotnet.exe" else "dotnet")
        if (dotNetCliFile.exists()) {
            logger.info("dotNetCliPath: ${dotNetCliFile.canonicalPath}")
            project.extra["dotNetCliPath"] = dotNetCliFile.canonicalPath
            return dotNetCliFile.canonicalPath
        }
    }
    logger.warn(".NET Core CLI not found. dotnet.cmd will be used")
    return null
}

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

tasks {
    jar { dependsOn(":protocol:generateModels") }

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
        dependsOn(":protocol:generateModels")
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
        dependsOn(":protocol:generateModels", prepareNuGetConfig)

        inputs.file(file(dotnetSolution))
        inputs.dir(file("$repoRoot/src/dotnet"))
        outputs.dir(file("$repoRoot/src/dotnet/RiderPlugin.UnrealLink/bin/RiderPlugin.UnrealLink/$buildConfigurationProp"))

        doLast {
            val warningsAsErrors: String by project.extra

            val dotNetCliPath = findDotNetCliPath()
            val slnDir = dotnetSolution.parentFile
            val buildArguments = listOf(
                "build",
                dotnetSolution.canonicalPath,
                "/p:Configuration=$buildConfigurationProp",
                "/p:Version=${project.version}",
                "/p:TreatWarningsAsErrors=$warningsAsErrors",
                "/v:${project.properties.getOrDefault("dotnetVerbosity", "minimal")}",
                "/bl:${dotnetSolution.name}.binlog",
                "/nologo"
            )
            if (dotNetCliPath != null) {
                logger.info("dotnet call: '$dotNetCliPath' '$buildArguments' in '$slnDir'")
                project.exec {
                    executable = dotNetCliPath
                    args = buildArguments
                    workingDir = dotnetSolution.parentFile
                }
            } else {
                logger.info("call dotnet.cmd with '$buildArguments'")
                project.exec {
                    executable = "$rootDir/tools/dotnet.cmd"
                    args = buildArguments
                    workingDir = dotnetSolution.parentFile
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE") val buildPlugin by getting(Zip::class) {
        dependsOn(buildResharperHost)
        outputs.upToDateWhen { false }
        buildSearchableOptions {
            enabled = buildConfigurationProp == "Release"
        }
        val outputDir = File("$rootDir/output")
        outputs.dir(outputDir)
        doLast {
            val buildDir = File("${project.projectDir}/build/")
            copy {
                from("$buildDir/distributions/${rootProject.name}-${project.version}.zip")
                into(outputDir)
            }
        }
    }

    withType<PrepareSandboxTask> {
        val packCppSide = getByName("packCppSide")
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
}

changelog {
    version = project.version.toString()
    // https://github.com/JetBrains/gradle-changelog-plugin/blob/main/src/main/kotlin/org/jetbrains/changelog/Changelog.kt#L23
    // This is just common semVerRegex with the addition of a forth optional group (number) ( x.x.x[.x][-alpha43] )
    headerParserRegex =
        """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\.?(0|[1-9]\d*)?(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)
            (?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?${'$'}"""
            .trimMargin().toRegex()
    groups = listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Known Issues")
    keepUnreleasedSection = true
    itemPrefix = "-"
}

intellij {
    type = "RD"
    instrumentCode = false
    downloadSources = false

    setPlugins("com.jetbrains.rider-cpp")

    val dependencyPath = File(projectDir, "dependencies")
    if (dependencyPath.exists()) {
        localPath = dependencyPath.canonicalPath
        println("Will use ${File(localPath, "build.txt").readText()} from $localPath as RiderSDK")
    } else {
        version = "${project.property("majorVersion")}-SNAPSHOT"
        println("Will download and use build/riderRD-$version as RiderSDK")
    }

    tasks {
        val dumpCurrentChangelog by registering {
            val outputFile = File("${project.buildDir}/release_notes.md")
            outputs.file(outputFile)
            doLast { outputFile.writeText(currentReleaseNotesAsMarkdown) }
        }

        // PatchPluginXml gets latest (always Unreleased) section from current changelog and write it into plugin.xml
        // dumpCurrentChangelog dumps the same section to file (for Marketplace changelog)
        // After, patchChangelog rename [Unreleased] to [202x.x.x.x] and create new empty Unreleased.
        // So order is important!
        patchPluginXml { changeNotes( closure { currentReleaseNotesAsHtml }) }
        patchChangelog { mustRunAfter(patchPluginXml, dumpCurrentChangelog) }

        publishPlugin {
            dependsOn(patchPluginXml, dumpCurrentChangelog, patchChangelog)
            token(System.getenv("UNREALLINK_intellijPublishToken"))

            val pubChannels = project.findProperty("publishChannels")
            if ( pubChannels != null) {
                val chan = pubChannels.toString().split(',')
                println("Channels for publish $chan")
                channels(chan)
            } else {
                channels(listOf("alpha"))
            }
        }
    }
}

	val currentBranchName = getBranchName()
    val currentReleaseNotesAsHtml =
        """
            <body>
            <p><b>New in "${project.version}"</b></p>
            <p>${changelog.getLatest().toHTML()}</p>
            <p>See the <a href="https://github.com/JetBrains/UnrealLink/blob/$currentBranchName/CHANGELOG.md">CHANGELOG</a> for more details and history.</p>
            </body>
        """.trimIndent()

    val currentReleaseNotesAsMarkdown =
        """
            ## New in ${project.version}
            ${changelog.getLatest().toText()}
            See the [CHANGELOG](https://github.com/JetBrains/UnrealLink/blob/$currentBranchName/CHANGELOG.md) for more details and history.
        """.trimIndent()
