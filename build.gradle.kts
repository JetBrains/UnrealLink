import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.changelog.closure
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.0"

    id("org.jetbrains.changelog") version "0.4.0"
    id("org.jetbrains.intellij") version "0.4.21"
    id("com.jetbrains.rdgen") version "0.203.161"
}

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "6.6.1"
    distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion}-all.zip"
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}

val repoRoot by extra { project.rootDir }
val sdkVersion by extra { "2020.3" }

val dotNetSdkPath by lazy {
    val sdkPath = intellij.ideaDependency.classes.resolve("lib").resolve("DotNetSdkForRdPlugins")
    assert(sdkPath.isDirectory)
    println(".NETSDK path: $sdkPath")

    return@lazy sdkPath
}


val dotNetDir by extra { File(repoRoot, "src/dotnet") }
val dotNetSolutionId by extra { "UnrealLink" }
val idePluginId by extra { "RiderPlugin" }
val dotNetRiderProjectId by extra {"$idePluginId.$dotNetSolutionId"}
val dotNetBinDir by extra { dotNetDir.resolve("$idePluginId.$dotNetSolutionId").resolve("bin") }
val dotNetPluginId by extra { "$idePluginId.${project.name}" }
val pluginPropsFile by extra { File(repoRoot, "build/generated/DotNetSdkPath.props") }
val dotnetSolution by extra { File(repoRoot, "$dotNetSolutionId.sln") }

val isWindows by extra { Os.isFamily(Os.FAMILY_WINDOWS) }


java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val buildConfiguration = (ext.properties.getOrPut("BuildConfiguration") { "Release" } as String)

project.version = (ext.properties.getOrPut("pluginVersion") { "${properties["productVersion"]}.${properties["BuildCounter"]}" } as String)

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

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

apply(from = "cpp.gradle.kts")

fun findDotNetCliPath(): String {
    if (project.extra.has("dotNetCliPath")) {
        val dotNetCliPath = project.extra["dotNetCliPath"] as String
        logger.info("dotNetCliPath (cached): $dotNetCliPath")
        return dotNetCliPath
    }

    val pathComponents = System.getenv("PATH").split(File.pathSeparatorChar)
    for (dir in pathComponents) {
        val dotNetCliFile = File(dir, if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            "dotnet.exe"
        } else {
            "dotnet"
        })
        if (dotNetCliFile.exists()) {
            logger.info("dotNetCliPath: ${dotNetCliFile.canonicalPath}")
            project.extra["dotNetCliPath"] = dotNetCliFile.canonicalPath
            return dotNetCliFile.canonicalPath
        }
    }
    error(".NET Core CLI not found. Please add: 'dotnet' in PATH")
}

tasks {
    val prepareRiderBuildProps by creating {
        group = "RiderBackend"
        doLast {
            val buildDir = File("${project.projectDir}/build/")
            buildDir.mkdirs()
            val propsFile = buildDir.resolve("DotNetSdkPath.generated.props")

            val dotNetSdkFile = dotNetSdkPath
//            project.buildServer.progress("Generating :${propsFile.canonicalPath}...")
            project.file(propsFile).writeText("""<Project>
            |  <PropertyGroup>
            |    <DotNetSdkPath>${dotNetSdkFile.canonicalPath}</DotNetSdkPath>
            |  </PropertyGroup>
            |</Project>""".trimMargin())
        }
    }

    val prepareNuGetConfig by creating {
        group = "RiderBackend"
        dependsOn(prepareRiderBuildProps)
        doLast {
            val nuGetConfigFile = project.projectDir.resolve("NuGet.Config")

            val dotNetSdkFile = dotNetSdkPath
            logger.info("dotNetSdk location: '$dotNetSdkFile'")
            assert(dotNetSdkFile.isDirectory)

//            project.buildServer.progress("Generating :${nuGetConfigFile.canonicalPath}...")
            val nugetConfigText = """<?xml version="1.0" encoding="utf-8"?>
        |<configuration>
        |  <packageSources>
        |    <clear />
        |    <add key="local-dotnet-sdk" value="${dotNetSdkFile.canonicalPath}" />
        |    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
        |  </packageSources>
        |</configuration>
        """.trimMargin()
            nuGetConfigFile.writeText(nugetConfigText)

            logger.info("Generated content:\n$nugetConfigText")

            val sb = StringBuilder("Dump dotNetSdkFile content:\n")
            dotNetSdkFile.listFiles()?.forEach { file ->
                sb.append("${file.canonicalPath}\n")
            }
            logger.info(sb.toString())
        }
    }


    val buildResharperHost by creating {
        group = "RiderBackend"
        description = "Build backend for Rider"
        dependsOn(":protocol:generateModels")
        dependsOn(prepareNuGetConfig)
        doLast {
            val buildConfiguration = project.extra["BuildConfiguration"] as String
            val warningsAsErrors = project.extra["warningsAsErrors"] as String
            val file = dotnetSolution

//            project.buildServer.progress("Building $file ($buildConfiguration)")

            val dotNetCliPath = findDotNetCliPath()
            val slnDir = file.parentFile
            val verbosity = "normal"
            val buildArguments = listOf(
                    "build",
                    file.canonicalPath,
                    "/p:Configuration=$buildConfiguration",
                    "/p:Version=${project.version}",
                    "/p:TreatWarningsAsErrors=$warningsAsErrors",
                    "/v:$verbosity",
                    "/bl:${file.name+".binlog"}",
                    "/nologo")

            logger.info("dotnet call: '$dotNetCliPath' '$buildArguments' in '$slnDir'")
            project.exec {
                executable = dotNetCliPath
                args = buildArguments
                workingDir = file.parentFile
            }
        }

    }

    @Suppress("UNUSED_VARIABLE") val dumpChangelogResult by creating() {
        doLast {
            File("release_notes.md").writeText(
"""## New in ${project.version}
${changelog.get(project.version as String)}

See the [CHANGELOG](https://github.com/JetBrains/UnrealLink/blob/net202/CHANGELOG.md) for more details and history.
""".trimIndent())
        }
    }

    @Suppress("UNUSED_VARIABLE") val buildPlugin by getting(Zip::class) {
        dependsOn(buildResharperHost)
        outputs.upToDateWhen { false }
        buildSearchableOptions {
            enabled = buildConfiguration == "Release"
        }
        doLast {
            copy {
                from("$buildDir/distributions/${rootProject.name}-${project.version}.zip")
                into("$rootDir/output")
            }
        }
    }

    jar.get().dependsOn(":protocol:generateModels")

    withType<PrepareSandboxTask> {
        dependsOn(buildResharperHost)
        val packCppSide = getByName<Zip>("packCppSide")
        dependsOn(packCppSide)

        outputs.upToDateWhen { false } //need to dotnet artifacts be included when only dotnet sources were changed

        val outputFolder = dotNetBinDir
                .resolve(dotNetPluginId)
                .resolve(buildConfiguration)
        val dllFiles = listOf(
                File(outputFolder, "$dotNetPluginId.dll"),
                File(outputFolder, "$dotNetPluginId.pdb")
        )
        doLast {
            dllFiles.forEach { file ->
                copy {
                    from(file)
                    into("${intellij.sandboxDirectory}/plugins/${intellij.pluginName}/dotnet")
                }
            }

            dllFiles.forEach { file ->
                if (!file.exists()) throw RuntimeException("File $file does not exist")
            }
            copy {
                from(packCppSide.archiveFile)
                into("${intellij.sandboxDirectory}/plugins/${intellij.pluginName}/EditorPlugin")
            }
        }
    }
}

changelog {
    groups = listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Known Issues")
    keepUnreleasedSection = true
}

intellij {
    type = "RD"
    version = "$sdkVersion-SNAPSHOT"

    instrumentCode = false
    downloadSources = false
    tasks.withType<PatchPluginXmlTask> {
        val isReleaseBuild = properties["isReleaseBuild"].toString().toBoolean()

        if(isReleaseBuild) {
            val riderSdkVersion = properties["riderSdkVersion"] as String?
            if(riderSdkVersion != null && riderSdkVersion.isNotEmpty()) {
                sinceBuild(riderSdkVersion)
                untilBuild("$riderSdkVersion.*")
            }
        }

        val changelogProject = if (isReleaseBuild)
            changelog.getLatest()
        else
            changelog.getUnreleased()
        changeNotes(closure {
        """
            <body>
            <p><b>New in "${project.version}"</b></p>
            <p>
            ${changelogProject.toHTML()}
            </p>
            <p>See the <a href="https://github.com/JetBrains/UnrealLink/blob/net202/CHANGELOG.md">CHANGELOG</a> for more details and history.</p>
            </body>
        """.trimIndent()
        })
    }
}


