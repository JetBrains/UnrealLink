package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.model.PlayState
import icons.UnrealIcons
import javax.swing.Icon

abstract class PlayStateAction(text: String?, description: String?, icon: Icon?) : AnAction(text, description, icon) {
    override fun update(e: AnActionEvent) {
        val host = e.getHost()
        e.presentation.isVisible = host?.isUnrealEngineSolution ?: false
        e.presentation.isEnabled = host?.isConnectedToUnrealEditor ?: false
    }
}

class PlayInUnrealAction : PlayStateAction(
    UnrealLinkBundle.message("action.RiderLink.PlayInUnrealAction.text"),
    UnrealLinkBundle.message("action.RiderLink.PlayInUnrealAction.description"),
    UnrealIcons.Status.Play
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val value = e.getHost()?.playState
        e.presentation.isEnabled = e.presentation.isEnabled && value != PlayState.Play
        e.presentation.text =
            if (value == PlayState.Pause)
                UnrealLinkBundle.message("action.RiderLink.ResumeInUnrealAction.text")
            else
                UnrealLinkBundle.message("action.RiderLink.PlayInUnrealAction.text")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        host.model.playStateFromRider.fire(PlayState.Play)
    }
}

class StopInUnrealAction : PlayStateAction(
    UnrealLinkBundle.message("action.RiderLink.StopInUnrealAction.text"),
    UnrealLinkBundle.message("action.RiderLink.StopInUnrealAction.description"),
    UnrealIcons.Status.Stop
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost() ?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState != PlayState.Idle
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        host.model.playStateFromRider.fire(PlayState.Idle)
    }
}

class PauseInUnrealAction : PlayStateAction(
    UnrealLinkBundle.message("action.RiderLink.PauseInUnrealAction.text"),
    UnrealLinkBundle.message("action.RiderLink.PauseInUnrealAction.description"),
    UnrealIcons.Status.Pause
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost() ?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState != PlayState.Idle
        e.presentation.icon =
            if (host.playState == PlayState.Pause) UnrealIcons.Status.FrameSkip else UnrealIcons.Status.Pause
        e.presentation.text =
            if (host.playState == PlayState.Pause)
                UnrealLinkBundle.message("action.RiderLink.SkipFrame.text")
            else
                UnrealLinkBundle.message("action.RiderLink.PauseInUnrealAction.text")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        when (host.playState) {
            PlayState.Play -> host.model.playStateFromRider.fire(PlayState.Pause)
            PlayState.Pause -> host.model.frameSkip.fire()
            else -> host.logger.error("[UnrealLink] Invalid play state for PauseInUnrealAction: ${host.playState}")
        }
    }
}