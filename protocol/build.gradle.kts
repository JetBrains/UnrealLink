import com.jetbrains.rd.generator.gradle.RdGenTask

plugins {
    // Version is configured in gradle.properties
    id("com.jetbrains.rdgen")
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/maven-central")
}

val isMonorepo = rootProject.projectDir != projectDir.parentFile
val unrealLinkRepoRoot: File = projectDir.parentFile

sourceSets {
    main {
        kotlin {
            srcDir(unrealLinkRepoRoot.resolve("protocol/src/main/kotlin/model"))
        }
    }
}

data class UnrealLinkGeneratorSettings(
    val ue4LibCsLibraryOutput: File,
    val ue4LibCppLibraryOutput: File,
    val ue4LibKtLibraryOutput: File,
    val riderModelCsOutput: File,
    val riderModelKtOutput: File,
    val csEditorOutput: File,
    val cppEditorOutput: File
)

val ktOutputRelativePath = "src/rider/main/kotlin/com/jetbrains/rider/model"

val unrealLinkGeneratorSettings = if (isMonorepo) {
    val monorepoRoot =
        buildscript.sourceFile?.parentFile?.parentFile?.parentFile?.parentFile?.parentFile ?: error("Cannot find products home")
    check(monorepoRoot.resolve(".ultimate.root.marker").isFile) {
        error("Incorrect location in monorepo: monorepoRoot='$monorepoRoot'")
    }
    val monorepoPreGeneratedRootDir = monorepoRoot.resolve("dotnet/Plugins/_UnrealLink.Pregenerated")
    val monorepoPreGeneratedFrontendDir = monorepoPreGeneratedRootDir.resolve("Frontend")
    val monorepoPreGeneratedBackendDir = monorepoPreGeneratedRootDir.resolve("BackendModel")
    val monorepoPreGeneratedCppDir = monorepoPreGeneratedRootDir.resolve("CppModel")
    val ktOutputMonorepoRoot = monorepoPreGeneratedFrontendDir.resolve(ktOutputRelativePath)

    UnrealLinkGeneratorSettings(
        monorepoPreGeneratedBackendDir.resolve("Library"),
        monorepoPreGeneratedCppDir.resolve( "Library"),
        ktOutputMonorepoRoot.resolve("Library"),
        monorepoPreGeneratedBackendDir.resolve("RdRiderProtocol"),
        ktOutputMonorepoRoot.resolve("RdRiderProtocol"),
        monorepoPreGeneratedBackendDir.resolve("RdEditorProtocol"),
        monorepoPreGeneratedCppDir.resolve("RdEditorProtocol")
    )
} else {
    val csOutputRoot = File(unrealLinkRepoRoot, "src/dotnet/RiderPlugin.UnrealLink/obj/model")
    val cppOutputRoot = File(unrealLinkRepoRoot, "src/cpp/RiderLink/Source/RiderLink/Public/Model")
    val ktOutputRoot = File(unrealLinkRepoRoot, ktOutputRelativePath)
    UnrealLinkGeneratorSettings(
        csOutputRoot.resolve("Library"),
        cppOutputRoot.resolve( "Library"),
        ktOutputRoot.resolve("Library"),
        csOutputRoot.resolve("RdRiderProtocol"),
        ktOutputRoot.resolve("RdRiderProtocol"),
        csOutputRoot.resolve("RdEditorProtocol"),
        cppOutputRoot.resolve("RdEditorProtocol")
    )
}

rdgen {
    verbose = true
    packages = "model.editorPlugin,model.lib.ue4,model.rider"

    generator {
        language = "csharp"
        transform = "symmetric"
        root = "model.lib.ue4.UE4Library"
        directory = unrealLinkGeneratorSettings.ue4LibCsLibraryOutput.absolutePath
        generatedFileSuffix = ".Pregenerated"
    }

    generator {
        language = "cpp"
        transform = "reversed"
        root = "model.lib.ue4.UE4Library"
        directory = unrealLinkGeneratorSettings.ue4LibCppLibraryOutput.absolutePath
        generatedFileSuffix = ".Pregenerated"
    }

    generator {
        language = "kotlin"
        transform = "asis"
        root = "model.lib.ue4.UE4Library"
        directory = unrealLinkGeneratorSettings.ue4LibKtLibraryOutput.absolutePath
        generatedFileSuffix = ".Pregenerated"
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        directory = unrealLinkGeneratorSettings.riderModelCsOutput.absolutePath
        generatedFileSuffix = ".Pregenerated"
    }

    generator {
        language = "kotlin"
        transform = "asis"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        directory = unrealLinkGeneratorSettings.riderModelKtOutput.absolutePath
        generatedFileSuffix = ".Pregenerated"
    }

    generator {
        language = "csharp"
        transform = "asis"
        root = "model.editorPlugin.RdEditorRoot"
        directory = unrealLinkGeneratorSettings.csEditorOutput.absolutePath
        generatedFileSuffix = ".Pregenerated"
    }

    generator {
        language = "cpp"
        transform = "reversed"
        root = "model.editorPlugin.RdEditorRoot"
        directory = unrealLinkGeneratorSettings.cppEditorOutput.absolutePath
        generatedFileSuffix = ".Pregenerated"
    }
}

tasks.withType<RdGenTask> {
    dependsOn(sourceSets["main"].runtimeClasspath)
    classpath(sourceSets["main"].runtimeClasspath)
}

dependencies {
    if (isMonorepo) {
        implementation(project(":rider-model"))
    } else {
        val rdVersion: String by project
        val rdKotlinVersion: String by project

        implementation("com.jetbrains.rd:rd-gen:$rdVersion")
        implementation("org.jetbrains.kotlin:kotlin-stdlib:$rdKotlinVersion")
        implementation(
            project(
                mapOf(
                    "path" to ":",
                    "configuration" to "riderModel"
                )
            )
        )
    }
}
