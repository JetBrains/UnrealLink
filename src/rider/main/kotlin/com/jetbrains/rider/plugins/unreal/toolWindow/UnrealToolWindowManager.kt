package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.toArray
import com.intellij.util.toArray
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