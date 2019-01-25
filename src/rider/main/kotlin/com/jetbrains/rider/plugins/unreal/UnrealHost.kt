package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rdclient.util.idea.getLogger
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.util.idea.getComponent

class UnrealHost(project: Project): LifetimedProjectComponent(project) {
    companion object {
        val logger = getLogger<UnrealHost>()
        fun getInstance(project: Project) = project.getComponent<UnrealHost>()
    }
    val model = project.solution.rdRiderModel
    init {
//        var port = 0

//        val appDataLocal = "LOCALAPPDATA"
//        val userPath = System.getenv(appDataLocal)
//        val portFilePath = Paths.get(userPath, "RiderLink.txt")
//        val testFilePath = Paths.get(userPath, "TestPlugin.txt")
//        val testFile = File(testFilePath.toString())
//        testFile.createNewFile()
//        val outFile = File(portFilePath.toString())
//        val appLifetime = ApplicationManager.getApplication().createLifetime()
//        if(outFile.exists())
//        {
//            val lines = outFile.readLines()
//            port = lines.elementAt(0).toInt()
//        }
//        val dispatcher = FreeThreadedDispatcher(appLifetime)
//        val serializers = Serializers()
//        val identity = Identities(IdKind.Server)
//        protocol = Protocol(
//                serializers, identity, dispatcher,
//                SocketWire.Server(appLifetime, dispatcher, port = port, optId = "FrontendToBackend")
//        )
//        if(!outFile.exists())
//        {
//            outFile.writeText("$port$eol")
//
//        }
//        println("Server socket $port")
//        protocol.outOfSyncModels.view(appLifetime) { _, _ ->
//            ApplicationManager.getApplication().invokeLater {
//                throw IllegalStateException("Models are out of sync, please sync models between Rider and IDEA backend")
//            }
//        }

        model.test_connection.advise(componentLifetime) {
            println("Connection UE $it")
        }
//        this.model?.unreal_log.advise(appLifetime) {
//            println("UE_LOG: $it" )
//            logger.info("UE_LOG: $it")
//        }
    }
}