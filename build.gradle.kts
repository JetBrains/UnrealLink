import com.jetbrains.rd.generator.gradle.RdgenParams
import com.jetbrains.rd.generator.gradle.RdgenTask
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.intellij.tasks.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths

buildscript {
    repositories {
        maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
        maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
        maven { setUrl("https://cache-redirector.jetbrains.com/dl.bintray.com/kotlin/kotlin-eap") }
        maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
        maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
        maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
        mavenLocal()
    }

    dependencies {
        classpath(BuildPlugins.gradleIntellijPlugin)
        classpath(BuildPlugins.rdGenPlugin)
    }
}

plugins {
    id("java")
    id(Libraries.gradleIntellijPluginId)
    kotlin("jvm") version kotlinVersion
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}

val repoRoot by extra { project.rootDir }
val productVersion : String by project
val sdkVersion = productVersion
val sdkDirectory by extra { File(buildDir, "riderRD-$sdkVersion-SNAPSHOT") }
val reSharperHostSdkDirectory by extra { File(sdkDirectory, "/lib/ReSharperHostSdk") }
val rdLibDirectory by extra { File(sdkDirectory, "lib/rd") }

val dotNetDir by extra { File(repoRoot, "src/dotnet") }
val dotNetSolutionId by extra { "resharper_unreal" }
val dotNetRootId by extra { "ReSharperPlugin" }
val dotNetPluginId by extra { "$dotNetRootId.UnrealEditor" }
val pluginPropsFile by extra { File(dotNetDir, "Plugin.props") }
val dotnetSolution by extra { File(repoRoot, "$dotNetSolutionId.sln") }

val isWindows by extra { Os.isFamily(Os.FAMILY_WINDOWS) }

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
//    maven { setUrl("https://repo.labs.intellij.net/jitpack.io") }
//  mavenLocal()
//    flatDir { dirs(sdkDirectory.absolutePath) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

/*
tasks {
    wrapper {
        gradleVersion = "5.0"
        distributionType = Wrapper.DistributionType.ALL
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
    }
}
*/

val buildConfiguration = (ext.properties.getOrPut("BuildConfiguration") { "Release" } as String)

project.version = (ext.properties.getOrPut("pluginVersion") { "1.3.3.7" } as String)

tasks {
    withType<PublishTask> {
        if (project.extra.has("username"))
            setUsername(ext["username"] as String)
        if (project.extra.has("password"))
            setPassword(ext["password"] as String)
    }
    val version = "11_0_2b159"
//    val version = "11_0_3b304"
    withType<RunIdeTask> {
        setJbrVersion(version)
    }
    withType<BuildSearchableOptionsTask> {
        setJbrVersion(version)
    }
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("src/rider/main/kotlin")
            resources.srcDir("src/rider/main/resources")
        }
        test {
            kotlin.srcDir("src/rider/test/kotlin")
        }
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

val nugetPackagesPath by lazy {
    val sdkPath = intellij.ideaDependency.classes

    println("SDK path: $sdkPath")
    val path = File(sdkPath, "lib/ReSharperHostSdk")

    println("NuGet packages: $path")
    if (!path.isDirectory) error("$path does not exist or not a directory")

    return@lazy path
}

val riderSdkPackageVersion by lazy {
    val sdkPackageName = "JetBrains.Rider.SDK"

    val regex = Regex("${Regex.escape(sdkPackageName)}\\.([\\d\\.]+.*)\\.nupkg")
    val version = nugetPackagesPath
            .walk()
            .mapNotNull { regex.matchEntire(it.name)?.groupValues?.drop(1)?.first() }
            .singleOrNull() ?: error("$sdkPackageName package is not found in $nugetPackagesPath (or multiple matches)")
    println("$sdkPackageName version is $version")

    return@lazy version
}

tasks {
    val findMsBuild by creating {
        doLast {
            val stdout = ByteArrayOutputStream()

            val hostOs = System.getProperty("os.name")
            val isWindows by extra(hostOs.startsWith("Windows"))
            if (isWindows) {
                extra["executable"] = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\MSBuild\\Current\\Bin\\MSBuild.exe"
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
            pluginPropsFile.writeText("""
            |<Project>
            |   <PropertyGroup>
            |       <SdkVersion>${riderSdkPackageVersion}</SdkVersion>
            |       <Title>resharper_unreal</Title>
            |   </PropertyGroup>
            |</Project>""".trimMargin())
        }
    }

    val compileDotNet by creating {
        dependsOn(findMsBuild)
        dependsOn(patchPropsFile)
        doLast {
            exec {
                executable = findMsBuild.extra["executable"] as String
                args = listOf("/t:Restore;Rebuild", dotnetSolution.absolutePath, "/v:minimal", "/p:Configuration=$buildConfiguration")
            }
        }
    }

    val buildPlugin by getting(Zip::class) {
        dependsOn("findMsBuild")
        outputs.upToDateWhen { false }
        doLast {
            copy {
                from("$buildDir/distributions/${rootProject.name}-$archiveVersion.zip")
                into("$rootDir/output")
            }

            val changelogText = File("$repoRoot/CHANGELOG.md").readText()
            val changelogMatches = Regex("/(?s)(-.+?)(?=##|$)/").findAll(changelogText)
            val changeNotes = changelogMatches.toList().map {
                it.groups[1]!!.value.replace(Regex("/(?s)- /"), "\u2022 ").replace(Regex("/`/"), "").replace(Regex("/,/"), "%2C")
            }.take(1).joinToString("", "", "")

            exec {
                executable = getByName("findMsBuild").extra["executable"] as String
                args = listOf("/t:Pack", dotnetSolution.absolutePath, "/v:minimal", "/p:Configuration=$buildConfiguration", "/p:PackageOutputPath=$rootDir/output", "/p:PackageReleaseNotes=$changeNotes", "/p:PackageVersion=$version")
            }
        }
    }

    jar.get().dependsOn(":protocol:generateModel")
}

intellij {
    type = "RD"
    version = "$sdkVersion-SNAPSHOT"

    instrumentCode = false
    downloadSources = false
    updateSinceUntilBuild = false
}

tasks {
    withType<PatchPluginXmlTask> {
        val changelogText = File("$repoRoot/CHANGELOG.md").readText()
        val changelogMatches = Regex("""/(?s)(-.+?)(?=##|$)/""").findAll(changelogText)

        setChangeNotes(changelogMatches.map {
            it.groups[1]!!.value.replace(Regex("/(?s)\r?\n/"), "<br />\n")
        }.take(1).joinToString("", "", "")
        )
    }

    withType<PrepareSandboxTask> {
        dependsOn("compileDotNet")
        val outputFolder = Paths.get(
                dotNetDir.absolutePath,
                "$dotNetRootId.$dotNetSolutionId",
                "bin",
                dotNetPluginId,
                buildConfiguration).toFile()
        val dllFiles = listOf(
                File(outputFolder, "$dotNetPluginId.dll"),
                File(outputFolder, "$dotNetPluginId.pdb")
        )

        dllFiles.forEach { file ->
            copy {
                from(file.absolutePath)
                into("${intellij.sandboxDirectory}/plugins/${intellij.pluginName}/dotnet")
            }
        }

        doLast {
            dllFiles.forEach { file ->
                if (!file.exists()) throw RuntimeException("File $file does not exist")
            }
        }
    }
}

apply(from = "cpp.gradle.kts")