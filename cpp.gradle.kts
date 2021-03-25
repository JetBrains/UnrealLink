@file:Suppress("HardCodedStringLiteral")

import org.gradle.kotlin.dsl.support.listFilesOrdered
import java.io.ByteArrayOutputStream

val isWindows: Boolean by extra

fun findDotNetCliPath(): String? {
    if (project.extra.has("dotNetCliPath")) {
        val dotNetCliPath = project.extra["dotNetCliPath"] as String
        logger.info("dotNetCliPath (cached): $dotNetCliPath")
        return dotNetCliPath
    }

    val pathComponents = System.getenv("PATH").split(File.pathSeparatorChar)
    for (dir in pathComponents) {
        val dotNetCliFile = File(
            dir, if (isWindows) {
                "dotnet.exe"
            } else {
                "dotnet"
            }
        )
        if (dotNetCliFile.exists()) {
            logger.info("dotNetCliPath: ${dotNetCliFile.canonicalPath}")
            project.extra["dotNetCliPath"] = dotNetCliFile.canonicalPath
            return dotNetCliFile.canonicalPath
        }
    }
    logger.warn(".NET Core CLI not found. dotnet.cmd will be used")
    return null
}

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
        inputs.file(pathToUpluginTemplate)
        inputs.property("version", project.version)
        outputs.file(filePathToUplugin)
        doLast {
            if(filePathToUplugin.exists())
                filePathToUplugin.delete()

            pathToUpluginTemplate.copyTo(filePathToUplugin)

            val text = filePathToUplugin.readLines().map {
                it.replace("%PLUGIN_VERSION%", "${project.version}")
            }
            filePathToUplugin.writeText(text.joinToString(System.lineSeparator()))
        }
    }
    withType<Delete> {
        delete(patchUpluginVersion.outputs.files)
    }

    val riderLinkDir = File("$rootDir/src/cpp/RiderLink")

    val generateChecksum by creating {
        dependsOn(patchUpluginVersion)
        dependsOn(":protocol:generateModels")
        val upluginFile = riderLinkDir.resolve("RiderLink.uplugin.template")
        val resourcesDir = riderLinkDir.resolve("Resources")
        val sourceDir = riderLinkDir.resolve("Source")
        val checksumFile = riderLinkDir.resolve("checksum")
        inputs.file(upluginFile)
        inputs.dir(resourcesDir)
        inputs.dir(sourceDir)
        outputs.file(checksumFile)
        doLast {
            val inputFiles = sequence{
                yield(upluginFile)
                resourcesDir.listFilesOrdered().forEach { if(it.isFile) yield(it) }
                sourceDir.listFilesOrdered().forEach { if(it.isFile) yield(it) }
            }
            val instance = java.security.MessageDigest.getInstance("MD5")
            inputFiles.forEach { instance.update(it.readBytes()) }
            checksumFile.writeBytes(instance.digest())
        }
    }
    withType<Delete> {
        delete(generateChecksum.outputs.files)
    }

    val buildZipper by creating {
        description = "Build Zipper utility to pack RiderLink"

        val zipperSolution = File("$rootDir/tools/Zipper/Zipper.sln")
        inputs.file("$rootDir/tools/Zipper/Program.cs")
        inputs.file("$rootDir/tools/Zipper/Zipper.csproj")
        inputs.file(zipperSolution)
        val zipperFolder = File("$rootDir/tools/Zipper/bin/Release/net461")
        val zipperBinary = zipperFolder.resolve("Zipper.exe")
        outputs.file(zipperBinary)

        doLast {
            val dotNetCliPath = null
            val slnDir = zipperSolution.parentFile
            val buildArguments = listOf(
                "build",
                zipperSolution.absolutePath,
                "/p:Configuration=Release",
                "/nologo"
            )

            if (dotNetCliPath != null) {
                logger.info("dotnet call: '$dotNetCliPath' '$buildArguments' in '$slnDir'")
                project.exec {
                    executable = dotNetCliPath
                    args = buildArguments
                    workingDir = zipperSolution.parentFile
                }
            } else {
                logger.info("call dotnet.cmd with '$buildArguments'")
                project.exec {
                    executable = "$rootDir/tools/dotnet.cmd"
                    args = buildArguments
                    workingDir = zipperSolution.parentFile
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE") val packCppSide by creating {
        dependsOn(patchUpluginVersion)
        dependsOn(":protocol:generateModels")
        dependsOn(generateChecksum)
        dependsOn(buildZipper)

        inputs.dir("$rootDir/src/cpp/RiderLink")
        val outputZip = File("$rootDir/build/distributions/RiderLink.zip")
        outputs.file(outputZip)
        doLast {
            if(isWindows){
                project.exec {
                    executable = buildZipper.outputs.files.first().absolutePath
                    args = listOf(riderLinkDir.absolutePath, outputZip.absolutePath)
                    workingDir = rootDir
                }
            } else {
                project.exec {
                    executable = "zsh"
                    args = listOf("-c", "eval",  "`/usr/libexec/path_helper -s`", "&&", "mono", buildZipper.outputs.files.first().absolutePath, riderLinkDir.absolutePath, outputZip.absolutePath)
                }
            }
        }
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
                    commandLine = if(isWindows)
                        listOf("cmd.exe", "/c", "fsutil", "reparsepoint", "query", targetDir.absolutePath, "|", "find", "Print Name:")
                    else
                        listOf("find", targetDir.absolutePath, "-maxdepth", "1", "-type", "l", "-ls")

                    isIgnoreExitValue = true
                    standardOutput = stdOut
                }

                // Check if it's Junction to local RiderLink
                if(result.exitValue == 0) {
                    val output = stdOut.toString().trim()
                    if(output.isNotEmpty())
                    {
                        val pathToJunction = if(isWindows)
                            output.substringAfter("Print Name:").trim()
                        else
                            output.substringAfter("->").trim()
                        if(File(pathToJunction) == riderLinkDir) {
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
                    commandLine = if(isWindows)
                        listOf("cmd.exe", "/c", "mklink", "/J", targetDir.absolutePath, riderLinkDir.absolutePath)
                    else
                        listOf("ln", "-s", riderLinkDir.absolutePath, targetDir.absolutePath)
                    errorOutput = stdOut
                    isIgnoreExitValue = true
                }
            if (result.exitValue != 0) {
                println(stdOut.toString().trim())
            }
        }
    }
}
