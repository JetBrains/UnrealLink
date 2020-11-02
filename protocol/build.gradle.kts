import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenTask

val rdLibDirectory: File by rootProject.extra

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
    flatDir {
        dir(rdLibDirectory)
    }
}

plugins {
    id("java")
    kotlin("jvm")
    id("com.jetbrains.rdgen")
}
dependencies {
    implementation(kotlin("stdlib"))

    implementation(":rider-model")
    implementation(":rd-gen") // provided by sdk
}

val repoRoot: File by rootProject.extra

val modelDir = File(repoRoot, "protocol/src/main/kotlin/model")
val hashBaseDir = File(repoRoot, "build/rdgen")

tasks {
    val cppOutputRoot = File(repoRoot, "src/cpp/RiderLink/Source/RiderLink/Public/Model")
    val csOutputRoot = File(repoRoot, "src/dotnet/RiderPlugin.UnrealLink/obj/model")
    val ktOutputRoot = File(repoRoot, "src/rider/main/kotlin/com/jetbrains/rider/model")

    val generateUE4Lib by creating(RdGenTask::class) {
        configure<RdGenExtension> {
            verbose = project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG
            classpath("$rdLibDirectory/rider-model.jar")

            sources("$modelDir/lib/ue4")
            hashFolder = "$hashBaseDir/lib/ue4"
            packages = "model.lib.ue4"

            val csLibraryOutput = File(csOutputRoot, "Library")
            val cppLibraryOutput = File(cppOutputRoot, "Library")
            val ktLibraryOutput = File(ktOutputRoot, "Library")

            systemProperty("model.out.src.lib.ue4.csharp.dir", "$csLibraryOutput")
            systemProperty("model.out.src.lib.ue4.cpp.dir", "$cppLibraryOutput")
            systemProperty("model.out.src.lib.ue4.kt.dir", "$ktLibraryOutput")
        }
    }

    val generateRiderModel by creating(RdGenTask::class) {
        dependsOn(generateUE4Lib)

        configure<RdGenExtension> {
            // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
            // intellij SDK, which is extracted in afterEvaluate
            verbose = project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG
            classpath("$rdLibDirectory/rider-model.jar")

            sources("$modelDir")
            packages = "model.rider"
            hashFolder = "$hashBaseDir/rider"

            val csRiderOutput = File(csOutputRoot, "RdRiderProtocol")
            val ktRiderOutput = File(ktOutputRoot, "RdRiderProtocol")

            generator {
                language = "kotlin"
                transform = "asis"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                namespace = "com.jetbrains.rider.model"
                directory = "$ktRiderOutput"

            }

            generator {
                language = "csharp"
                transform = "reversed"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                namespace = "JetBrains.Rider.Model"
                directory = "$csRiderOutput"
            }
            systemProperty("model.out.src.rider.csharp.dir", "$csRiderOutput")
            systemProperty("model.out.src.rider.kotlin.dir", "$ktRiderOutput")
        }
    }


    val generateEditorPluginModel by creating(RdGenTask::class) {
        dependsOn(generateUE4Lib)

        configure<RdGenExtension> {
            verbose = project.gradle.startParameter.logLevel == LogLevel.INFO || project.gradle.startParameter.logLevel == LogLevel.DEBUG
            classpath("$rdLibDirectory/rider-model.jar")

            sources("$modelDir")
            hashFolder = "$hashBaseDir/editorPlugin"
            packages = "model.editorPlugin"

            val csEditorOutput = File(csOutputRoot, "RdEditorProtocol")
            val cppEditorOutput = File(cppOutputRoot, "RdEditorProtocol")

            systemProperty("model.out.src.editorPlugin.csharp.dir", "$csEditorOutput")
            systemProperty("model.out.src.editorPlugin.cpp.dir", "$cppEditorOutput")
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val generateModels by creating {
        group = "protocol"
        description = "Generates protocol models."
        dependsOn(generateEditorPluginModel)
        dependsOn(generateRiderModel)
    }

    withType<RdGenTask> {
        classpath("$rdLibDirectory/rd-gen.jar")
        dependsOn(build)
    }
}
