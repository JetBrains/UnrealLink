import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.IntelliJPlugin

// Note: The `buildscript` block affects the dependencies ONLY for the build scripts of the buildSrc projects
// (i.e. buildSrc/build.gradle.kts et al)
// Use top level `repositories` and `dependencies` for the buildSrc project itself. Also note that the dependencies of

apply(from = "../versions.gradle.kts")

plugins {
    `kotlin-dsl`
    java
    kotlin("jvm") version embeddedKotlinVersion
    id("org.jetbrains.intellij") version "0.4.7"
}

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
//    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
//    maven { setUrl("https://repo.labs.intellij.net/central-proxy") }
//    maven { setUrl("https://cache-redirector.jetbrains.com/myget.org.rd-snapshots.maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
}

dependencies {
    "compile"("gradle.plugin.org.jetbrains.intellij.plugins", "gradle-intellij-plugin", "0.4.7")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

intellij {
//    localPath = rootProject.buildDir.absolutePath
//    localSourcesPath = "../build"
    version = "2019.2-SNAPSHOT"
    type = "RD"
    instrumentCode = false
    downloadSources = false
}

extra["jbrVersion"] = "11_0_2b159"




/*
copy {
    from("build/riderRD-2019.2-SNAPSHOT")
    into("../build/riderRD-2019.2-SNAPSHOT")
}
*/
