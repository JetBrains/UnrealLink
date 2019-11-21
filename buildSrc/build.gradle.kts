// Note: The `buildscript` block affects the dependencies ONLY for the build scripts of the buildSrc projects
// (i.e. buildSrc/build.gradle.kts et al)
// Use top level `repositories` and `dependencies` for the buildSrc project itself. Also note that the dependencies of

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
//    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
//    maven { setUrl("https://repo.labs.intellij.net/central-proxy") }
//    maven { setUrl("https://cache-redirector.jetbrains.com/myget.org.rd-snapshots.maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
}

plugins {
    `kotlin-dsl`
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}


dependencies {
    compile("gradle.plugin.org.jetbrains.intellij.plugins", "gradle-intellij-plugin", "0.4.13")
}