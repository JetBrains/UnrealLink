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

    val patchUpluginVersion by creating {
        val pathToUpluginTemplate = File("${project.rootDir}/src/cpp/RiderLink/RiderLink.uplugin.template")
        val filePathToUplugin = File("${project.rootDir}/src/cpp/RiderLink/RiderLink.uplugin")
        if(filePathToUplugin.exists().not()) {
            pathToUpluginTemplate.copyTo(filePathToUplugin)
        }

        val text = filePathToUplugin.readLines().map {
            val pattern = "\"VersionName\": "
            it.replaceAfter(pattern, "\"${project.version}\",")
        }
        filePathToUplugin.writeText(text.joinToString(System.lineSeparator()))
    }

    val riderLinkDir = "$rootDir/src/cpp/RiderLink"

    @Suppress("UNUSED_VARIABLE") val packCppSide by creating(Zip::class) {
        dependsOn(patchUpluginVersion)
        from("${project.rootDir}/src/cpp/RiderLink") {
            include("RiderLink.uplugin", "Resources/**", "Source/**")
        }
        archiveFileName.set("RiderLink.zip")
    }

    @Suppress("UNUSED_VARIABLE") val symlinkPluginToUnrealProject by creating {
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
