package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.util.idea.getLogger

class UnrealToolWindowManager(project: Project,
                              private val host: UnrealHost,
                              private val unrealToolWindowFactory: UnrealToolWindowFactory
)
    : LifetimedProjectComponent(project) {
    companion object {
        private val myLogger = getLogger<UnrealToolWindowManager>()
    }

    init {
//        host.model?.unreal_log.whenTrue(componentLifetime) {
//            myLogger.info("new session")
//            val context = unrealToolWindowFactory.getOrCreateContext()
//            val shouldReactivateBuildToolWindow = context.isActive
//
//            if (shouldReactivateBuildToolWindow) {
//                context.activateToolWindowIfNotActive()
//            }
//        }

        host.model.unrealLog.advise(componentLifetime) { message ->
            val context = unrealToolWindowFactory.getOrCreateContext()

             context.addEvent(message.unrealLogMessage.message)
        }
    }
}