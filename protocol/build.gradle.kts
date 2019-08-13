repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
    flatDir {
        val rdLibDirectory = (rootProject.extra["rdLibDirectory"] as File).absolutePath
        dirs(rdLibDirectory)
        dir("C:\\Work\\JetBrains.Rider-2019.2-EAP7D-192.5895.894.Checked.win\\lib")
        dir("C:\\Work\\rd\\rd-kt\\rd-gen\\build\\libs")
    }
}

plugins {
//    `kotlin-dsl`
    id("java")
    kotlin("jvm")
//    id("com.jetbrains.rdgen")
}


dependencies {
    implementation(kotlin("stdlib"))

    compile(files("C:\\Work\\riderRD-2019.2-SNAPSHOT\\lib\\rd\\rider-model.jar"))
    compile(files("C:\\Work\\rd\\rd-kt\\rd-gen\\build\\libs\\rd-gen.jar"))
    compile(group = "com.jetbrains.rd", name = "rd-gen")
}
