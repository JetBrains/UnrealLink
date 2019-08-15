package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.util.idea.getLogger

class UnrealToolWindowManager(project: Project,
                              host: UnrealHost
) : LifetimedProjectComponent(project) {
    companion object {
        private val logger = getLogger<UnrealToolWindowManager>()
    }

    private val unrealToolWindowFactory = ServiceManager.getService(project, UnrealToolWindowFactory::class.java)

    init {
        host.model.unrealLog.advise(componentLifetime) { unrealLogMessage ->
            unrealToolWindowFactory.print(unrealLogMessage)
        }
    }
}