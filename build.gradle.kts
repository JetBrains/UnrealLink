import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

buildscript {
    repositories {
        mavenCentral()
        maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
        maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
        maven { setUrl("https://cache-redirector.jetbrains.com/dl.bintray.com/kotlin/kotlin-eap") }
        maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
        maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    }

    dependencies {
        classpath("gradle.plugin.org.jetbrains.intellij.plugins", "gradle-intellij-plugin", "0.4.21")
        classpath("com.jetbrains.rd", "rd-gen", "0.202.118")
    }
}

plugins {
    id("org.jetbrains.intellij") version "0.4.21"
    kotlin("jvm") version "1.3.72"
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}

val repoRoot by extra { project.rootDir }
val sdkVersion = "2020.2"
val sdkDirectory by extra { File(buildDir, "riderRD-$sdkVersion-SNAPSHOT") }
val reSharperHostSdkDirectory by extra { File(sdkDirectory, "/lib/DotNetSdkForRdPlugins") }
val rdLibDirectory by extra { File(sdkDirectory, "lib/rd") }

val dotNetDir by extra { File(repoRoot, "src/dotnet") }
val dotNetSolutionId by extra { "UnrealLink" }
val idePluginId by extra { "RiderPlugin" }
val dotNetRiderProjectId by extra {"$idePluginId.$dotNetSolutionId"}
val dotNetBinDir by extra { dotNetDir.resolve("$idePluginId.$dotNetSolutionId").resolve("bin") }
val dotNetPluginId by extra { "$idePluginId.${project.name}" }
val pluginPropsFile by extra { File(repoRoot, "build/generated/DotNetSdkPath.props") }
val dotnetSolution by extra { File(repoRoot, "$dotNetSolutionId.sln") }

val isWindows by extra { Os.isFamily(Os.FAMILY_WINDOWS) }

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
}

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

val dotNetSdkPath by lazy {
    val sdkPath = intellij.ideaDependency.classes.resolve("lib").resolve("DotNetSDkForRdPlugins")
    assert(sdkPath.isDirectory)
    println(".NETSDK path: $sdkPath")

    return@lazy sdkPath
}

apply(from = "cpp.gradle.kts")

tasks {
    val findMsBuild by creating {
        doLast {
            val stdout = ByteArrayOutputStream()

            if (isWindows) {
                exec {
                    executable = "${project.rootDir}/tools/vswhere.exe"
                    args = listOf("-latest", "-products", "*", "-requires", "Microsoft.Component.MSBuild", "-property", "installationPath")
                    standardOutput = stdout
                    workingDir = project.rootDir
                }
                val vsRootDir = stdout.toString().trim()
                var msBuildPath = "$vsRootDir/MSBuild/Current/Bin/MSBuild.exe"
                if (!file(msBuildPath).exists())
                    msBuildPath = "$vsRootDir/MSBuild/15.0/Bin/MSBuild.exe"
                extra["executable"] = msBuildPath
            } else {
                exec {
                    executable = "which"
                    args = listOf("msbuild")
                    standardOutput = stdout
                    workingDir = project.rootDir
                }
                extra["executable"] = stdout.toString().trim()
            }
        }
    }

    val patchPropsFile by creating {
        doLast {
            if (pluginPropsFile.parentFile.exists().not()) {
                pluginPropsFile.parentFile.mkdirs()
            }
            pluginPropsFile.writeText("""
            |<Project>
            |   <PropertyGroup>
            |       <DotNetSdkPath>${dotNetSdkPath}</DotNetSdkPath>
            |   </PropertyGroup>
            |</Project>""".trimMargin())
        }
    }

    val compileDotNet by creating {
        dependsOn(findMsBuild)
        dependsOn(":protocol:generateModel")
        dependsOn(patchPropsFile)

        inputs.files(dotNetDir.listFiles())
        outputs.dirs(dotNetBinDir)

        doLast {
            val stdout = ByteArrayOutputStream()
            val result = exec {
                executable = findMsBuild.extra["executable"] as String
                standardOutput = stdout
                args = listOf("/t:Restore;Rebuild", dotnetSolution.absolutePath, "/v:detailed", "/p:Configuration=$buildConfiguration")
                isIgnoreExitValue = true
            }

            if (result.exitValue != 0) {
                println(stdout.toString().trim())
                throw GradleException("Problems with compileDotNet task")
            }
        }

    }

    @Suppress("UNUSED_VARIABLE") val buildPlugin by getting(Zip::class) {
        dependsOn(compileDotNet)
        outputs.upToDateWhen { false }
        buildSearchableOptions {
            enabled = buildConfiguration == "Release"
        }
        doLast {
            copy {
                from("$buildDir/distributions/${rootProject.name}-${project.version}.zip")
                into("$rootDir/output")
            }

            val changelogText = File("$repoRoot/CHANGELOG.md").readText()
            val changelogMatches = Regex("/(?s)(-.+?)(?=##|$)/").findAll(changelogText)
            val changeNotes = changelogMatches.toList().map {
                it.groups[1]!!.value.replace(Regex("/(?s)- /"), "\u2022 ").replace(Regex("/`/"), "").replace(Regex("/,/"), "%2C")
            }.take(1).joinToString("", "", "")
        }
    }

    jar.get().dependsOn(":protocol:generateModel")

    withType<PrepareSandboxTask> {
        dependsOn(compileDotNet)
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


intellij {
    type = "RD"
    version = "$sdkVersion-SNAPSHOT"

    instrumentCode = false
    downloadSources = false
    tasks.withType<PatchPluginXmlTask> {}
}


