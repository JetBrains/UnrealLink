package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.util.idea.getLogger

class UnrealToolWindowManager(project: Project,
                              host: UnrealHost,
                              unrealToolWindowContextFactory: UnrealToolWindowContextFactory
) : LifetimedProjectComponent(project) {
    companion object {
        private val logger = getLogger<UnrealToolWindowManager>()
    }

    private val context = unrealToolWindowContextFactory.context

    init {
        host.model.unrealLog.advise(componentLifetime) { rdLogMessage ->
            context.print(rdLogMessage.message.data)
        }
    }
}