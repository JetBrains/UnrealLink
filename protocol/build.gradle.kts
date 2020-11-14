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
