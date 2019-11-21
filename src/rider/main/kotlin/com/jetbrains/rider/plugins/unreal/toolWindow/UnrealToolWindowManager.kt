package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.util.idea.getLogger

class UnrealToolWindowManager(project: Project,
                              host: UnrealHost,
                              private val unrealToolWindowContextFactory: UnrealToolWindowFactory
) : LifetimedProjectComponent(project) {
    companion object {
        private val logger = getLogger<UnrealToolWindowManager>()
    }

//    private val context = unrealToolWindowContextFactory

    init {
        host.model.unrealLog.advise(componentLifetime) { unrealLogMessage ->
            unrealToolWindowContextFactory.print(unrealLogMessage)
        }
    }
}