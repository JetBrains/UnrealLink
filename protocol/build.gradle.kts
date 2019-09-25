repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
    /*flatDir {
        val rdLibDirectory = (rootProject.extra["rdLibDirectory"] as File).absolutePath
        dirs(rdLibDirectory)
    }*/
}

plugins {
//    `kotlin-dsl`
    id("java")
    kotlin("jvm")
    id("com.jetbrains.rdgen")
}



dependencies {
    val rdLibDirectory by rootProject.extra.properties

    implementation(kotlin("stdlib"))

    compile(files("$rdLibDirectory/rider-model.jar"))
    compile(files("$rdLibDirectory/rd-gen.jar"))
    compile(group = "com.jetbrains.rd", name = "rd-gen")
}
