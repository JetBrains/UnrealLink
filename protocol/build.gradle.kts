
plugins {
    id("java")
    kotlin("jvm")
    id("com.jetbrains.rdgen")
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib")
    compile(group = "com.jetbrains.rd", name = "rd-gen")
    compile(files("C:\\Work\\riderRD-2019.2-SNAPSHOT\\lib\\rd\\rider-model.jar"))
}

repositories {
    mavenCentral()
    flatDir {
        val rdLibDirectory = (rootProject.extra["rdLibDirectory"] as File).absolutePath
        dirs(rdLibDirectory)
        dir("C:\\Work\\JetBrains.Rider-2019.2-EAP7D-192.5895.894.Checked.win\\lib")
    }
    files()
}
