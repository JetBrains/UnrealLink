package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.Icon

class UnrealHostOperations {
    companion object {
        fun updatePresentationBasedOnUnrealAvailability(e: AnActionEvent, connectedIcon: Icon, disconnectedIcon: Icon) {
            val host = e.getUnrealHost()
            if (host == null || !host.isUnrealEngineSolution) {
                e.presentation.isEnabledAndVisible = false
                return
            }

            e.presentation.isEnabledAndVisible = true
            e.presentation.icon = if (host.isConnectedToUnrealEditor) connectedIcon else disconnectedIcon
        }
    }
}