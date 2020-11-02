package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.reactive.whenTrue
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.UE4Library

class UnrealToolWindowManager(project: Project,
                              host: UnrealHost,
                              private val unrealToolWindowContextFactory: UnrealToolWindowFactory
) : LifetimedProjectComponent(project) {

    init {
        UE4Library.registerSerializersCore(host.model.serializationContext.serializers)

        host.model.isConnectedToUnrealEditor.whenTrue(componentLifetime) {
            unrealToolWindowContextFactory.showTabForNewSession()
        }

        host.model.unrealLog.advise(componentLifetime) { event ->
            unrealToolWindowContextFactory.print(event)
        }
    }
}
