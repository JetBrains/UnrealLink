import java.io.File
import java.io.ByteArrayOutputStream

tasks {
    val getUnrealEngineProject by creating {
        doLast {
            val ueProjectPathTxt = rootDir.resolve("UnrealEngineProjectPath.txt")
            if (ueProjectPathTxt.exists()) {
                val ueProjectPath = ueProjectPathTxt.readText()
                val ueProjectPathDir = File(ueProjectPath)
                if (ueProjectPathDir.exists()) {
                    val isUEProject = ueProjectPathDir.listFiles().any {
                        it.extension == "uproject"
                    }
                    if (isUEProject) {
                        extra["UnrealProjectPath"] = ueProjectPathDir
                    } else {
                        throw AssertionError("Add path to a valid UnrealEngine project folder to: $ueProjectPathTxt")
                    }
                }
            } else {
                ueProjectPathTxt.createNewFile()
                throw AssertionError("Add path to a valid UnrealEngine project folder to: $ueProjectPathTxt")
            }
        }
    }
    val cloneRdCpp by creating (Exec::class) {
        val destinationDir = buildDir.resolve("rd")
        val branchName = "ue4-adapt"
        enabled = !destinationDir.exists()
        commandLine = listOf("git", "clone", "--branch=${branchName}", "https://github.com/jetbrains/rd.git", destinationDir.absolutePath, "--quiet")
    }

    val rdCppFolder = "$buildDir/rd/rd-cpp"

    val buildRdCpp by creating(Exec::class) {
        dependsOn(cloneRdCpp)
        inputs.dir("$rdCppFolder/src")
        outputs.dir("$rdCppFolder/export")
        commandLine = listOf("cmd", "/c", "$rdCppFolder/build.cmd")
        //windows only
    }

    val riderLinkDir = "$rootDir/src/cpp/RiderLink"

    val installRdCpp by creating {
        dependsOn(buildRdCpp)
        val exportIncludeFolder = "$rdCppFolder/export/include"
        val exportLibsFolder ="$rdCppFolder/export/Libs"
        val includeDir = "$riderLinkDir/Source/RD/include"
        val libDir = "$riderLinkDir/Source/RD/libs"
        inputs.dir(exportIncludeFolder)
        inputs.dir(exportLibsFolder)
        outputs.dirs(includeDir, libDir)

        doLast {

            delete(files(includeDir, libDir))
            copy {
                from(exportIncludeFolder)
                into(includeDir)
            }
            copy {
                from(exportLibsFolder)
                into(libDir)
            }
        }
    }

    val symlinkPluginToUnrealProject by creating {
        dependsOn(installRdCpp)
        dependsOn(getUnrealEngineProject)
        doLast {
            val unrealProjectPath = getUnrealEngineProject.extra["UnrealProjectPath"] as File
            val targetDir = File("$unrealProjectPath/Plugins/RiderLink")

            if(targetDir.exists())
                throw StopExecutionException()
            val stdOut = ByteArrayOutputStream()
            val result = exec {
                    commandLine = listOf("cmd.exe", "/c", "mklink" , "/J" ,targetDir.absolutePath ,File(riderLinkDir).absolutePath)
                    errorOutput = stdOut
                    isIgnoreExitValue = true
                }
            if (result.exitValue != 0) {
                println("${stdOut.toString().trim()}")
            }
        }
    }
}