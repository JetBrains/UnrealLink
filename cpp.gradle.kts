tasks {
    val cloneRdCpp by creating {
        doLast {
            val destinationDir = buildDir.resolve("rd")
            if (!destinationDir.exists()) {
                exec {
                    executable = "git"
                    setArgs(listOf("clone", "https://github.com/jetbrains/rd.git", destinationDir.absolutePath, "--quiet"))
                }
            }
        }
    }

    val rdCppFolder = "$buildDir/rd/rd-cpp"

    val buildRdCpp by creating(Exec::class) {
        dependsOn(cloneRdCpp)
        commandLine = listOf("cmd", "/c", "$rdCppFolder/build.cmd")
        //windows only
    }

    val installRdCpp by creating {
        dependsOn(buildRdCpp)
        doLast {
            val UE4RootPath = "C:\\Work\\UnrealEngine\\"//todo
            val includeDir = "$UE4RootPath/Engine/Plugins/Developer/RiderLink/Source/RiderLink/include"
            val libDir = "$UE4RootPath/Engine/Plugins/Developer/RiderLink/Source/RiderLink/libs"

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