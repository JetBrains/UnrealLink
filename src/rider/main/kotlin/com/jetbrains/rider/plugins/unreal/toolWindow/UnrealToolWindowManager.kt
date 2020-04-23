package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.reactive.whenTrue
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.model.UE4Library
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.UnrealLogViewerManager
import com.jetbrains.rider.util.idea.getComponent
import com.jetbrains.rider.util.idea.getLogger

class UnrealToolWindowManager(project: Project,
                              host: UnrealHost,
                              private val unrealToolWindowContextFactory: UnrealToolWindowFactory
) : LifetimedProjectComponent(project) {
    companion object {
        private val logger = getLogger<UnrealToolWindowManager>()
    }

    val unrealLogViewerManager = project.getComponent<UnrealLogViewerManager>()

    init {
        UE4Library.registerSerializersCore(host.model.serializationContext.serializers)

        host.model.isConnectedToUnrealEditor.whenTrue(componentLifetime) {
            unrealToolWindowContextFactory.showTabForNewSession()
        }

        host.model.unrealLog.advise(componentLifetime) { event ->
            unrealToolWindowContextFactory.print(event)
            unrealToolWindowContextFactory.flush()
        }
    }
}
