package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.protocolOrThrow
import com.jetbrains.rd.util.reactive.whenTrue
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.UE4Library

class UnrealToolWindowManager(project: Project) : LifetimedProjectComponent(project) {
    init {
        val host = UnrealHost.getInstance(project)

        UE4Library.registerSerializersCore(host.model.protocolOrThrow.serializers)

        host.model.isConnectedToUnrealEditor.whenTrue(componentLifetime) {
            val toolWindowsFactory = UnrealToolWindowFactory.getInstance(project)
            toolWindowsFactory.showTabForNewSession()
        }
    }
}
