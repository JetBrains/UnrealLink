package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.platform.util.idea.LifetimedProjectService
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.PlayState
import icons.UnrealIcons
import javax.swing.Icon

@Service
class PlayStateActionStateService(project: Project) : LifetimedProjectService(project) {
    companion object {
        fun getInstance(project: Project): PlayStateActionStateService = project.service()
    }

    private var disabledUntilModelChange: Boolean = false

    init {
        val host = UnrealHost.getInstance(project)
        host.performModelAction {
            it.isConnectedToUnrealEditor.change.advise(projectServiceLifetime) {
                invalidate()
            }
        }
        host.playStateModel.change.advise(projectServiceLifetime) {
            invalidate()
        }
    }

    fun invalidate() {
        disabledUntilModelChange = false
        forceTriggerUIUpdate()
    }

    fun disableUntilStateChange() {
        disabledUntilModelChange = true
    }

    fun isDisabledUntilStateChange() = disabledUntilModelChange
}

abstract class PlayStateAction(text: String?, description: String?, icon: Icon?) : AnAction(text, description, icon) {
    override fun update(e: AnActionEvent) {
        val host = e.getHost()

        e.presentation.isVisible = host?.isUnrealEngineSolution ?: false
        e.presentation.isEnabled = host?.isConnectedToUnrealEditor ?: false

        if (e.presentation.isEnabled) {
            val project = host?.project ?: return
            val state = PlayStateActionStateService.getInstance(project)
            e.presentation.isEnabled = !state.isDisabledUntilStateChange()
        }
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
        val state = PlayStateActionStateService.getInstance(host.project)
        state.disableUntilStateChange()
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
        val state = PlayStateActionStateService.getInstance(host.project)
        state.disableUntilStateChange()
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
        val state = PlayStateActionStateService.getInstance(host.project)
        when (host.playState) {
            PlayState.Play -> host.model.playStateFromRider.fire(PlayState.Pause).also { state.disableUntilStateChange() }
            PlayState.Pause -> host.model.frameSkip.fire().also { state.disableUntilStateChange() }
            else -> host.logger.error("[UnrealLink] Invalid play state for PauseInUnrealAction: ${host.playState}")
        }
    }
}