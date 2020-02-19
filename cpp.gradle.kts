import java.io.File

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
        val cloneCommand = listOf("git", "clone", "--branch=$branchName", "git@github.com:JetBrains/rd.git", destinationDir.absolutePath, "--quiet")
        val pullCommand = listOf("git", "--git-dir", destinationDir.resolve(".git").absolutePath, "pull", "origin", branchName)
        commandLine = if (destinationDir.exists()) pullCommand else cloneCommand
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

            if(targetDir.exists()) {
                val stdOut = java.io.ByteArrayOutputStream()
                // Check if it's Junction
                val result = exec {
                    commandLine = listOf("cmd.exe", "/c", "fsutil", "reparsepoint", "query", targetDir.absolutePath, "|", "find", "Print Name:")
                    isIgnoreExitValue = true
                    standardOutput = stdOut
                }

                // Check if it's Junction to local RiderLink
                if(result.exitValue == 0) {
                    val output = stdOut.toString().trim().split(" ")
                    if(!output.isEmpty())
                    {
                        val pathToJunction = output.last()
                        if(File(pathToJunction) == File(riderLinkDir)) {
                            println("Junction is already correct")
                            throw StopExecutionException()
                        }
                    }
                }

                // If it's not Junction or if it's a Junction but doesn't point to local RiderLink - delete it
                exec {
                    commandLine = listOf("cmd.exe", "/c", "rmdir", "/S", "/Q", targetDir.absolutePath)
                }
            }
            val stdOut = java.io.ByteArrayOutputStream()
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
