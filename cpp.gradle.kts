import java.io.File
import java.io.ByteArrayOutputStream

tasks {
    val getUnrealEngineProject by creating {
        doLast {
            val ueProjectPathTxt = rootDir.resolve("UnrealEngineProjectPath.txt")
            if (ueProjectPathTxt.exists()) {
                val ueProjectPath = ueProjectPathTxt.readText()
                val ueProjectPathDir = File(ueProjectPath)
                if (!ueProjectPathDir.exists()) throw AssertionError("$ueProjectPathDir doesn't exist")
                if (!ueProjectPathDir.isDirectory) throw AssertionError("$ueProjectPathDir is not directory")

                val isUEProject = ueProjectPathDir.listFiles()?.any {
                    it.extension == "uproject"
                }
                if (isUEProject == true) {
                    extra["UnrealProjectPath"] = ueProjectPathDir
                } else {
                    throw AssertionError("Add path to a valid UnrealEngine project folder to: $ueProjectPathTxt")
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
        val cloneCommand = listOf("git", "clone", "--branch=$branchName", "https://github.com/JetBrains/rd.git", destinationDir.absolutePath, "--quiet")
        val pullCommand = listOf("git", "--git-dir", destinationDir.resolve(".git").absolutePath, "pull", "origin", branchName)
        commandLine = if (destinationDir.exists()) pullCommand else cloneCommand
    }

    val updateSubmodulesRdCpp by creating (Exec::class) {
        workingDir = buildDir.resolve("rd")
        commandLine = listOf("git", "submodule", "update", "--init", "--recursive")
    }

    val rdCppFolder = "$buildDir/rd/rd-cpp"

    val buildRdCpp by creating(Exec::class) {
        dependsOn(cloneRdCpp)
        dependsOn(updateSubmodulesRdCpp)
        inputs.dir("$rdCppFolder/src")
        outputs.dir("$rdCppFolder/export")
        commandLine = listOf("cmd", "/c", "$rdCppFolder/build.cmd")
        //windows only
    }

    val patchUpluginVersion by creating {
        val pathToUplugin = "${project.rootDir}/src/cpp/RiderLink/RiderLink.uplugin"

        val text = File(pathToUplugin).readLines().map {
            val pattern = "\"VersionName\": "
            it.replaceAfter(pattern, "\"${project.version}\",")
        }
        File(pathToUplugin).writeText(text.joinToString(System.lineSeparator()))
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

    @Suppress("UNUSED_VARIABLE") val packCppSide by creating(Zip::class) {
        dependsOn(installRdCpp)
        dependsOn(patchUpluginVersion)
        from("${project.rootDir}/src/cpp/RiderLink") {
            include("RiderLink.uplugin", "Resources/**", "Source/**")
        }
        archiveFileName.set("RiderLink.zip")
    }

    @Suppress("UNUSED_VARIABLE") val symlinkPluginToUnrealProject by creating {
        dependsOn(installRdCpp)
        dependsOn(getUnrealEngineProject)
        dependsOn(patchUpluginVersion)
        doLast {
            val unrealProjectPath = getUnrealEngineProject.extra["UnrealProjectPath"] as File
            val targetDir = File("$unrealProjectPath/Plugins/Developer/RiderLink")

            if(targetDir.exists()) {
                val stdOut = ByteArrayOutputStream()
                // Check if it's Junction
                val result = exec {
                    commandLine = listOf("cmd.exe", "/c", "fsutil", "reparsepoint", "query", targetDir.absolutePath, "|", "find", "Print Name:")
                    isIgnoreExitValue = true
                    standardOutput = stdOut
                }

                // Check if it's Junction to local RiderLink
                if(result.exitValue == 0) {
                    val output = stdOut.toString().trim().split(" ")
                    if(output.isNotEmpty())
                    {
                        val pathToJunction = output.last()
                        if(file(pathToJunction) == file(riderLinkDir)) {
                            println("Junction is already correct")
                            throw StopExecutionException()
                        }
                    }
                }

                // If it's not Junction or if it's a Junction but doesn't point to local RiderLink - delete it
                targetDir.delete()
            }

            targetDir.parentFile.mkdirs();
            val stdOut = ByteArrayOutputStream()
            val result = exec {
                    commandLine = listOf("cmd.exe", "/c", "mklink" , "/J" ,targetDir.absolutePath ,File(riderLinkDir).absolutePath)
                    errorOutput = stdOut
                    isIgnoreExitValue = true
                }
            if (result.exitValue != 0) {
                println(stdOut.toString().trim())
            }
        }
    }
}
