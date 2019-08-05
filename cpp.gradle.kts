tasks {
    create<Exec>("cloneRdCpp") {
        //    executable "git"
//    args "clone", "https://github.com/jetbrains/rd.git", "$buildDir/rd", "--quiet"
    }

    val rdCppFolder = "$buildDir/rd/rd-cpp"

    create<Exec>("buildRdCpp") {
        dependsOn("cloneRdCpp")
        commandLine = listOf("cmd", "/c", "$rdCppFolder/build.cmd")
        //windows only
    }

    create("installRdCpp") {
        dependsOn("buildRdCpp")
        doLast {
            val UE4RootPath = "${rootProject.projectDir}"//todo
            val includeDir = "$UE4RootPath/src/cpp/Source/RiderLink/include"
            val libDir = "$UE4RootPath/src/cpp/Source/RiderLink/libs"

            delete(files(includeDir, libDir))
            copy {
                from("$rdCppFolder/export/include")
                into(includeDir)
            }
            copy {
                from("$rdCppFolder/export/Libs")
                into(libDir)
            }
        }

    }
}