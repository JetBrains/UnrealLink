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
                e.presentation.isEnabled = value != 1
            }
        }

        e.presentation.isVisible = true
        e.presentation.isEnabled = if (host == null) false else host.model.play.valueOrDefault(0) != 1
        super.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost()
        host?.model?.play?.set(1)
    }
}

class StopInUnrealAction : AnAction("Stop Unreal", "Stop Unreal", AllIcons.Actions.Suspend) {
    private var subscribed = false
    override fun update(e: AnActionEvent) {
        val host = e.getHost()

        if (!subscribed) {
            subscribed = true
            host?.model?.play?.advise(e.project!!.lifetime) { value ->
                e.presentation.isEnabled = value > 0
            }
        }

        e.presentation.isVisible = true
        e.presentation.isEnabled = if (host == null) false else host.model.play.valueOrDefault(0) > 0
        super.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost()
        host?.model?.play?.set(0)
    }
}

class PauseInUnrealAction : AnAction("Pause Unreal", "Pause Unreal", AllIcons.Actions.Pause) {
    private var subscribed = false

    override fun update(e: AnActionEvent) {
        val host = e.getHost()

        if (!subscribed) {
            subscribed = true
            host?.model?.play?.advise(e.project!!.lifetime) { value ->
                e.presentation.isEnabled = value == 1
            }
        }

        e.presentation.isVisible = true
        e.presentation.isEnabled = if (host == null) false else host.model.play.valueOrDefault(0) == 1
        super.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost()
        host?.model?.play?.set(2)
    }
}

fun AnActionEvent.getHost(): UnrealHost? {
    val project = project?: return null
    return UnrealHost.getInstance(project)
}