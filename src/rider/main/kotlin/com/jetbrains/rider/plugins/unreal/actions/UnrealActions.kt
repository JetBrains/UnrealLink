package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.reactive.valueOrDefault
import com.jetbrains.rider.plugins.unreal.UnrealHost
import icons.RiderIcons

class PlayInUnrealAction : AnAction("Play Unreal", "Play Unreal", AllIcons.Actions.Execute) {
    private var subscribed = false

    override fun update(e: AnActionEvent) {
        val host = e.getHost()

        if (!subscribed) {
            subscribed = true
            host?.model?.play?.advise(e.project!!.lifetime) { value ->
                e.presentation.isEnabled = !value
            }
        }

        e.presentation.isVisible = true
        e.presentation.isEnabled = !(host?.model?.play?.valueOrDefault(false)!!)
        super.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost()
        host?.model?.play?.set(true)
    }
}

class StopInUnrealAction : AnAction("Stop Unreal", "Stop Unreal", AllIcons.Actions.Suspend) {
    private var subscribed = false
    override fun update(e: AnActionEvent) {
        val host = e.getHost()

        if (!subscribed) {
            subscribed = true
            host?.model?.play?.advise(e.project!!.lifetime) { value ->
                e.presentation.isEnabled = value
            }
        }

        e.presentation.isVisible = true
        e.presentation.isEnabled = host?.model?.play?.valueOrDefault(false)!!
        super.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost()
        host?.model?.play?.set(false)
    }
}

fun AnActionEvent.getHost(): UnrealHost? {
    val project = project?: return null
    return UnrealHost.getInstance(project)
}