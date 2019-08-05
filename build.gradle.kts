import groovy.util.FileNameFinder
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

buildscript {

    repositories {
        maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
        maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
        maven { setUrl("https://cache-redirector.jetbrains.com/dl.bintray.com/kotlin/kotlin-eap") }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.21")
    }


}
plugins {
    id("java")
    id("org.jetbrains.intellij")
    id("org.jetbrains.kotlin.jvm") version "1.3.21"
}
dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

val repoRoot = project.rootDir
extra.apply {
    set("repoRoot", repoRoot)
    set("isWindows", Os.isFamily(Os.FAMILY_WINDOWS))
    val sdkVersion = "2019.2"
    set("sdkVersion", sdkVersion)
    set("rdLibDirectory", File(repoRoot, "build/riderRD-$sdkVersion-SNAPSHOT/lib/rd"))
    set("reSharperHostSdkDirectory", File(repoRoot, "build/riderRD-$sdkVersion-SNAPSHOT/lib/ReSharperHostSdk"))
    val dotNetDir = File(repoRoot, "src/dotnet")
    set("dotNetDir", dotNetDir)
    set("pluginPropsFile", File(dotNetDir, "Plugin.props"))
    val dotNetRootId = "ReSharperPlugin"
    set("dotNetRootId", dotNetRootId)
    set("dotNetPluginId", "${dotNetRootId}.UnrealEditor")
    val dotNetSolutionId = "resharper_unreal"
    set("dotNetSolutionId", dotNetSolutionId)
    set("dotnetSolution", File(repoRoot, "${dotNetSolutionId}.sln"))
}

repositories {
    maven { setUrl("https://repo.labs.intellij.net/central-proxy") }
    maven { setUrl("https://repo.labs.intellij.net/rd-snapshots-maven") }
//  maven { url "https://repo.labs.intellij.net/jitpack.io" }
//  mavenLocal()
    flatDir { dirs((extra["rdLibDirectory"] as File).absolutePath) }
    mavenCentral()
}

tasks {
    task<Wrapper>("wrapper") {
        gradleVersion = "4.10"
        distributionType = Wrapper.DistributionType.ALL
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion}-all.zip"
    }
}

val buildConfiguration = ext.properties["BuildConfiguration"] ?: "Release"

project.version = ext.properties["pluginVersion"] ?: "1.3.3.7"

if (extra.has("username"))
    intellij.publish.username = ext["username"] as String

if (ext.has("password"))
    intellij.publish.password = ext["password"] as String

the<JavaPluginConvention>().sourceSets {
    "main" {
        java {
            srcDir("src/rider/main/kotlin")
        }
        resources {
            srcDir("src/rider/main/resources")
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks {
    create("findMsBuild") {
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

    create("patchPropsFile") {
        doLast {
            val getFiles = FileNameFinder().getFileNames("${project.extra["reSharperHostSdkDirectory"]}", "JetBrains.Rider.SDK.*.nupkg")
            val riderSdkFile = getFiles.find { !it.contains("Test") }!!
            val versionGroup = (Regex("""JetBrains\.Rider\.SDK\.([\d.]+.*)\.nupkg""").findAll(riderSdkFile)).toList()
            assert(versionGroup.isNotEmpty())
            assert(1 == versionGroup.size)
            val version = versionGroup[0].value
            (project.extra["pluginPropsFile"] as File).writeText("""<Project>
              <PropertyGroup>
                <SdkVersion>${version}</SdkVersion>
                <Title>resharper_unreal</Title>
              </PropertyGroup>
            </Project>""")
        }
    }

    create("compileDotNet") {
        dependsOn("findMsBuild")
        dependsOn("patchPropsFile")
        doLast {
            exec {
                executable = project.tasks.getByName("findMsBuild").extra["executable"] as String
                args = listOf("/t:Restore;Rebuild", "${project.extra["dotnetSolution"]}", "/v:minimal", "/p:Configuration=$buildConfiguration")
            }
        }
    }
}


tasks {
    named<Zip>("buildPlugin") {
        dependsOn("findMsBuild")
        outputs.upToDateWhen { false }
        doLast {
            copy {
                from("$buildDir/distributions/$rootProject.name-${version}.zip")
                into("$rootDir/output")
            }

            val changelogText = File("${repoRoot}/CHANGELOG.md").readText()
            val changelogMatches = Regex("/(?s)(-.+?)(?=##|$)/").findAll(changelogText)
            val changeNotes = changelogMatches.toList().map {
                it.groups[1]!!.value.replace(Regex("/(?s)- /"), "\u2022 ").replace(Regex("/`/"), "").replace(Regex("/,/"), "%2C")
            }.take(1).joinToString("", "", "")

            exec {
                executable = tasks.getByName("findMsBuild").extra["executable"] as String
                args = listOf("/t:Pack", "${project.extra["dotnetSolution"]}", "/v:minimal", "/p:Configuration=${buildConfiguration}", "/p:PackageOutputPath=$rootDir/output", "/p:PackageReleaseNotes=$changeNotes", "/p:PackageVersion=$version")
            }
        }
    }
}
intellij {
    //    type = "RD"
//    version = "$sdkVersion-SNAPSHOT"
    localPath = "C:\\Work\\JetBrains.Rider-2019.2-EAP3D-192.5308.646.Checked.win"
    downloadSources = false
}

//apply(plugin = "com.jetbrains.rdgen")
apply(from = "model.gradle.kts")


tasks {
    withType<PatchPluginXmlTask> {
        val changelogText = File("${repoRoot}/CHANGELOG.md").readText()
        val changelogMatches = Regex("""/(?s)(-.+?)(?=##|$)/""").findAll(changelogText)

        setChangeNotes(changelogMatches.map {
            it.groups[1]!!.value.replace(Regex("/(?s)\r?\n/"), "<br />\n")
        }.take(1).joinToString("", "", "")
        )
    }

    named<PrepareSandboxTask>("prepareSandbox") {
        dependsOn("compileDotNet")
        val outputFolder = "${project.extra["dotNetDir"]}/" +
                "${project.extra["dotNetRootId"]}.${project.extra["dotNetSolutionId"]}/" +
                "bin/" +
                "${project.extra["dotNetPluginId"]}/$buildConfiguration"
        val dllFiles = listOf(
                "$outputFolder/${project.extra["dotNetPluginId"]}.dll",
                "$outputFolder/${project.extra["dotNetPluginId"]}.pdb"
        )

        dllFiles.forEach { f ->
            val file = File(f)
            from(file)
            into("${intellij.pluginName}/dotnet")
        }

        doLast {
            dllFiles.forEach { f ->
                val file = file(f)
                if (!file.exists()) throw RuntimeException("File $file does not exist")
            }
        }
    }
}

apply(from = "cpp.gradle.kts")



