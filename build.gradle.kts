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
//    maven { setUrl("https://repo.labs.intellij.net/central-proxy") }
//    maven { setUrl("https://cache-redirector.jetbrains.com/myget.org.rd-snapshots.maven") }
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

val repoRoot = project.rootDir
val sdkDirectory = File(buildDir, "riderRD-$sdkVersion-SNAPSHOT")
val reSharperHostSdkDirectory = File(sdkDirectory, "/lib/ReSharperHostSdk")
val rdLibDirectory = File(sdkDirectory, "lib/rd")

val dotNetDir = File(repoRoot, "src/dotnet")
val dotNetSolutionId = "resharper_unreal"
val dotNetRootId = "ReSharperPlugin"
val dotNetPluginId = "$dotNetRootId.UnrealEditor"
val pluginPropsFile = File(dotNetDir, "Plugin.props")

extra.apply {
    set("repoRoot", repoRoot)
    set("isWindows", Os.isFamily(Os.FAMILY_WINDOWS))
    set("sdkVersion", sdkVersion)
    set("rdLibDirectory", rdLibDirectory)
    set("reSharperHostSdkDirectory", reSharperHostSdkDirectory)
    set("dotNetDir", dotNetDir)
    set("pluginPropsFile", pluginPropsFile)
    set("dotNetRootId", dotNetRootId)
    set("dotNetPluginId", dotNetPluginId)
    set("dotNetSolutionId", dotNetSolutionId)
    set("dotnetSolution", File(repoRoot, "$dotNetSolutionId.sln"))
}

repositories {
//    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
//  maven { url "https://repo.labs.intellij.net/jitpack.io" }
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
//    val version = "11_0_2b159"
//    val version = "11_0_3b304"
    val f = File(reSharperHostSdkDirectory, "jbr/bin/java.exe")
    withType<RunIdeTask> {
        //        setJbrVersion(version)
        setExecutable(f)
    }
    withType<BuildSearchableOptionsTask> {
        //        setJbrVersion(version)
        setExecutable(f)
    }

}

the<JavaPluginConvention>().sourceSets {
    "main" {
        java {
            srcDir("src/rider/main/kotlin")
        }
        resources {
            srcDir("src/rider/main/resources")
        }
    }
    "test" {
        java {
            srcDir("src/rider/test/kotlin")
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
            .listFiles()
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
//            val version = File(sdkDirectory, "build.txt").bufferedReader().readLine().drop(3)
            //drop "RD-" prefix

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
                args = listOf("/t:Restore;Rebuild", "${project.extra["dotnetSolution"]}", "/v:minimal", "/p:Configuration=$buildConfiguration")
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
                args = listOf("/t:Pack", "${project.extra["dotnetSolution"]}", "/v:minimal", "/p:Configuration=$buildConfiguration", "/p:PackageOutputPath=$rootDir/output", "/p:PackageReleaseNotes=$changeNotes", "/p:PackageVersion=$version")
            }
        }
    }
}

intellij {
    type = "RD"
    version = "$sdkVersion-SNAPSHOT"
//    downloadSources = false
}

apply(plugin = Libraries.rdGenPluginId)
//apply(from = "model.gradle.kts")

val modelDir = File(repoRoot, "protocol/src/main/kotlin/model")
val hashBaseDir = File(repoRoot, "build/rdgen")

configure<RdgenParams> {
    verbose = true
    classpath("${rootProject.extra["rdLibDirectory"]}/rider-model.jar", "" +
            "C:\\Work\\resharper-unreal\\protocol\\build\\classes\\kotlin\\main")
}

tasks {
    //    val unrealEditorCppOutput = File(repoRoot, "src/cpp/Source/RiderLink/Private/RdEditorProtocol")
    val unrealEditorCppOutput = File("C:\\Work\\UnrealEngine\\Engine\\Plugins\\Developer\\RiderLink\\Source\\RiderLink\\Private\\RdEditorProtocol")
    val csEditorOutput = File(repoRoot, "src/dotnet/ReSharperPlugin.resharper_unreal/model/RdEditorProtocol")
    val csRiderOutput = File(repoRoot, "src/dotnet/ReSharperPlugin.resharper_unreal/model/RdRiderProtocol")
    val csLibraryOutput = File(repoRoot, "src/dotnet/ReSharperPlugin.resharper_unreal/model/Library")
    val ktOutput = File(repoRoot, "src/rider/main/kotlin/com/jetbrains/rider/model/RdRiderProtocol")

    create<RdgenTask>("generateRiderModel") {
        configure<RdgenParams> {
            // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
            // intellij SDK, which is extracted in afterEvaluate
//            verbose = true
//            classpath("${rootProject.extra["rdLibDirectory"]}/rider-model.jar")
            sources("$modelDir/rider")
            packages = "model.rider"
            hashFolder = "$hashBaseDir/rider"

            generator {
                language = "kotlin"
                transform = "asis"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                //            namespace = "com.jetbrains.rider.plugins.unreal"
                namespace = "com.jetbrains.rider.model"
                directory = "$ktOutput"

            }

            generator {
                language = "csharp"
                transform = "reversed"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                namespace = "JetBrains.Rider.Model"
                directory = "$csRiderOutput"
            }
            properties["model.out.src.rider.csharp.dir"] = "$csRiderOutput"
            properties["model.out.src.rider.kotlin.dir"] = "$ktOutput"
        }
    }


    create<RdgenTask>("generateEditorPluginModel") {
        configure<RdgenParams> {
            //            verbose = true
//            classpath("${rootProject.extra["rdLibDirectory"]}/rider-model.jar")
            sources("$modelDir/editorPlugin")
            hashFolder = "$hashBaseDir/editorPlugin"
            packages = "model.editorPlugin"
//            changeCompiled()

            properties["model.out.src.editorPlugin.csharp.dir"] = "$csEditorOutput"
            properties["model.out.src.editorPlugin.cpp.dir"] = "$unrealEditorCppOutput"
        }
    }

    create<RdgenTask>("generateUE4Lib") {
        configure<RdgenParams> {
            sources("$modelDir/lib/ue4")
            hashFolder = "$hashBaseDir/lib/ue4"
            packages = "model.lib.ue4"
            //            changeCompiled()

            generator {
                language = "kotlin"
                transform = "symmetric"
                root = "model.lib.ue4.UE4Library"
                namespace = "com.jetbrains.rider.model"
                directory = "$ktOutput"
            }

            generator {
                language = "csharp"
                transform = "symmetric"
                namespace = "JetBrains.Unreal.Lib"
                root = "model.lib.ue4.UE4Library"
                directory = "$csLibraryOutput"
            }

            generator {
                language = "cpp"
                transform = "reversed"
//                namespace = "Jetbrains.Unreal"
                namespace = "Jetbrains.EditorPlugin"
                root = "model.lib.ue4.UE4Library"
                directory = "$unrealEditorCppOutput"
            }
            properties["model.out.src.lib.ue4.csharp.dir"] = "$csLibraryOutput"
            properties["model.out.src.lib.ue4.cpp.dir"] = "$unrealEditorCppOutput"
            properties["model.out.src.lib.ue4.kt.dir"] = "$ktOutput"
        }
    }

    create("generateModel") {
        group = "protocol"
        description = "Generates protocol models."
        dependsOn("generateRiderModel", "generateEditorPluginModel", "generateUE4Lib")
    }

    withType<Jar> {
        dependsOn("generateModel")
    }

//    withType<com.jetbrains.rd.generator.gradle.RdGenTask> {
//                dependsOn(rootProject.project("protocol").task("build"))
//    }
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