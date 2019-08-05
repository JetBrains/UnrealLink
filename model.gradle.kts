import com.jetbrains.rd.generator.gradle.RdgenParams
import com.jetbrains.rd.generator.gradle.RdgenTask

buildscript {
    repositories {
        maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
        maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
        maven { setUrl("https://cache-redirector.jetbrains.com/dl.bintray.com/kotlin/kotlin-eap") }
        maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
        maven { setUrl("https://cache-redirector.jetbrains.com/myget.org.rd-snapshots.maven") }
    }

    dependencies {
        classpath("com.jetbrains.rd:rd-gen:0.192.36")
    }
}

val repoRoot = project.extra["repoRoot"] as File
val modelDir = File(repoRoot, "protocol/src/main/kotlin/model")
val hashBaseDir = File(repoRoot, "build/rdgen")

// TODO: Think about adding an msbuild task for rdgen
//apply(plugin = "com.jetbrains.rdgen")

tasks {
    create<RdgenTask>("generateRiderModel") {
        configure<RdgenParams> {
            //task generateRiderModel(type: tasks.getByName("rdgen").class) {
            val csOutput = File(repoRoot, "src/dotnet/ReSharperPlugin.resharper_unreal/model/RdRiderProtocol")
            val ktOutput = File(repoRoot, "src/rider/main/kotlin/com/jetbrains/rider/model/RdRiderProtocol")


            // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
            // intellij SDK, which is extracted in afterEvaluate
            verbose = true
            classpath("${project.extra["rdLibDirectory"]}/rider-model.jar")
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
                directory = "$csOutput"
            }
            properties["model.out.src.rider.csharp.dir"] = "$csOutput"
            properties["model.out.src.rider.kotlin.dir"] = "$ktOutput"
        }
    }

    create<RdgenTask>("generateEditorPluginModel") {
        configure<RdgenParams> {
            val backendCsOutput = File(repoRoot, "src/dotnet/ReSharperPlugin.resharper_unreal/model/RdEditorProtocol")
            val unrealEditorCppOutput = File(repoRoot, "src/cpp/Source/RiderLink/Private/RdEditorProtocol")


            verbose = true
            classpath("${project.extra["rdLibDirectory"]}/rider-model.jar")
            sources("$modelDir/editorPlugin")
            hashFolder = "$hashBaseDir/editorPlugin"
            packages = "model.editorPlugin"

            generator {
                language = "csharp"
                transform = "asis"
                namespace = "JetBrains.Platform.Unreal.EditorPluginModel"
                root = "model.editorPlugin.RdEditorModel"
                directory = "$backendCsOutput"
            }

            generator {
                language = "cpp"
                transform = "reversed"
                namespace = "jetbrains.editorplugin"
                root = "model.editorPlugin.RdEditorModel"
                directory = "$unrealEditorCppOutput"
            }
            properties["model.out.src.editorPlugin.csharp.dir"] = "$backendCsOutput"
            properties["model.out.src.editorPlugin.cpp.dir"] = "$unrealEditorCppOutput"
        }
    }

    create("generateModel") {
        group = "protocol"
        description = "Generates protocol models."
        dependsOn("generateRiderModel", "generateEditorPluginModel")
    }
    withType<Jar> {
        dependsOn("generateModel")
    }
}
// Make sure the dotnet build tasks depend on model, too
