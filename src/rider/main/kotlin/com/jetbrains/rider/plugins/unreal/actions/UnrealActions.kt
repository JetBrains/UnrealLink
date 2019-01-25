package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.jetbrains.rd.util.reactive.valueOrDefault
import com.jetbrains.rider.plugins.unreal.UnrealHost
import icons.RiderIcons

class PauseInUnrealAction : ToggleAction("Pause Unreal", "Pause Unreal", RiderIcons.FileTypes.Cpp) {
    override fun isSelected(e: AnActionEvent):Boolean {
        val unrealHost = e.getHost() ?: return false
        return unrealHost.model.play.valueOrDefault(false)
    }
    override fun setSelected(e: AnActionEvent, value: Boolean) {
        val host = e.getHost()
        host?.model?.play?.set(value)
    }

    override fun update(e: AnActionEvent) {

        e.presentation.isVisible = true
        e.presentation.isEnabled =  true
        super.update(e)
    }
}

fun AnActionEvent.getHost(): UnrealHost? {
    val project = project?: return null
    return UnrealHost.getInstance(project)
}