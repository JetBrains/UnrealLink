// Use these repositories to resolve plugins applied with the plugins { } block
pluginManagement {
    repositories {
        maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
        maven { setUrl("https://cache-redirector.jetbrains.com/dl.bintray.com/kotlin/kotlin-eap") }
        maven { setUrl("https://cache-redirector.jetbrains.com/myget.org.rd-snapshots.maven") }
    }
}

rootProject.name = "resharper_unreal"

include(":protocol")