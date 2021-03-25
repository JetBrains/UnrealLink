import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.changelog.closure
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.ByteArrayOutputStream


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

tasks {
    withType<PublishTask> {
        if (project.extra.has("username"))
            setUsername(ext["username"] as String)
        if (project.extra.has("password"))
            setPassword(ext["password"] as String)
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
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    withType<Delete> {
        delete("${dotnetSolution}.binlog")
    }
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
    var branchName = "net211"

    val stdOut = ByteArrayOutputStream()
    val result = project.exec {
        executable = "git"
        args = listOf("rev-parse", "--abbrev-ref", "HEAD")
        workingDir = projectDir
        standardOutput = stdOut
    }
    if(result.exitValue == 0) {
        val output = stdOut.toString().trim()
        if (output.isNotEmpty())
            return output
    }
    return branchName
}

tasks {
    val prepareRiderBuildProps by creating {
        group = "RiderBackend"

        val buildDir = File("${project.projectDir}/build/")
        val outputFile = buildDir.resolve("DotNetSdkPath.generated.props")

        inputs.property("dotNetSdkFile", { dotNetSdkPath.canonicalPath })
        outputs.file(outputFile)
        doLast {
            buildDir.mkdirs()

            project.file(outputFile).writeText(
                """<Project>
            |  <PropertyGroup>
            |    <DotNetSdkPath>${dotNetSdkPath.canonicalPath}</DotNetSdkPath>
            |  </PropertyGroup>
            |</Project>""".trimMargin()
            )
        }
    }
    setupCleanup(prepareRiderBuildProps)

    val prepareNuGetConfig by creating {
        group = "RiderBackend"
        dependsOn(prepareRiderBuildProps)

        val outputFile = project.projectDir.resolve("NuGet.Config")
        inputs.property("dotNetSdkFile", { dotNetSdkPath.canonicalPath })
        outputs.file(outputFile)
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
            outputFile.writeText(nugetConfigText)

            logger.info("Generated content:\n$nugetConfigText")
        }
    }

    setupCleanup(prepareNuGetConfig)

    val buildResharperHost by creating {
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

    @Suppress("UNUSED_VARIABLE") val dumpChangelogResult by creating() {
        group = "CI Release"
        val outputFile = File("release_notes.md")
        outputs.file(outputFile)
        doLast {
            val branchName = getBranchName()
            outputFile.writeText(
                """## New in ${project.version}
${changelog.get(project.version as String)}

See the [CHANGELOG](https://github.com/JetBrains/UnrealLink/blob/$branchName/CHANGELOG.md) for more details and history.
""".trimIndent())
        }
    }

    setupCleanup(dumpChangelogResult)

    @Suppress("UNUSED_VARIABLE") val buildPlugin by getting(Zip::class) {
        dependsOn(buildResharperHost)
        outputs.upToDateWhen { false }
        buildSearchableOptions {
            enabled = buildConfigurationProp == "Release"
        }
        val outputDir = File("$rootDir/output")
        outputs.dir(outputDir)
        doLast {
            copy {
                from("$buildDir/distributions/${rootProject.name}-${project.version}.zip")
                into(outputDir)
            }
        }
    }

    withType<Delete> {
        delete("$rootDir/output")
    }

    jar.get().dependsOn(":protocol:generateModels")

    withType<PrepareSandboxTask> {
        dependsOn(buildResharperHost)
        val packCppSide = getByName("packCppSide")
        dependsOn(packCppSide)

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
    groups = listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Known Issues")
    keepUnreleasedSection = true
    itemPrefix = "-"
}

intellij {
    type = "RD"
    instrumentCode = false
    downloadSources = false

    setPlugins("com.jetbrains.rider-cpp")

    val riderSdkVersion = project.property("majorVersion").toString()

    val dependencyPath = File(projectDir, "dependencies")
    if (dependencyPath.exists()) {
        localPath = dependencyPath.canonicalPath
        println("Will use ${File(localPath, "build.txt").readText()} from $localPath as RiderSDK")
    } else {
        version = "$riderSdkVersion-SNAPSHOT"
        println("Will download and use '$version' as RiderSDK")
    }

    tasks.withType<PatchPluginXmlTask> {
        val isReleaseBuild = buildConfigurationProp == "Release"
        var changelogProject = changelog.getUnreleased()

        if (isReleaseBuild) {
            sinceBuild(riderSdkVersion)
            untilBuild("$riderSdkVersion.*")

            changelogProject = changelog.getLatest()
        }

        val branchName = getBranchName()
        changeNotes(closure {
            """
            <body>
            <p><b>New in "${project.version}"</b></p>
            <p>
            ${changelogProject.toHTML()}
            </p>
            <p>See the <a href="https://github.com/JetBrains/UnrealLink/blob/$branchName/CHANGELOG.md">CHANGELOG</a> for more details and history.</p>
            </body>
        """.trimIndent()
        })
    }
}
