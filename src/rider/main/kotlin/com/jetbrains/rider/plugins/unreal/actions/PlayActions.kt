package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.*
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.PlayState
import icons.UnrealIcons
import javax.swing.Icon

abstract class PlayStateAction(text: String?, description: String?, icon: Icon?) : AnAction(text, description, icon) {
    override fun update(e: AnActionEvent) {
        val host = e.getHost()
        e.presentation.isVisible = host?.isUnrealEngineSolution?:false
        e.presentation.isEnabled = host?.isConnectedToUnrealEditor?:false
    }
}

class PlayInUnrealAction : PlayStateAction(UnrealLinkBundle.message("action.RiderLink.PlayInUnrealAction.text"),
    UnrealLinkBundle.message("action.RiderLink.PlayInUnrealAction.description"), UnrealIcons.Status.Play) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val value = e.getHost()?.playState
        e.presentation.isEnabled = e.presentation.isEnabled && value != PlayState.Play
        e.presentation.text = if (value == PlayState.Pause) UnrealLinkBundle.message("action.RiderLink.ResumeInUnrealAction.text") else UnrealLinkBundle.message("action.RiderLink.PlayInUnrealAction.text")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost()?:return
        host.model.playStateFromRider.fire(PlayState.Play)
    }
}

class StopInUnrealAction : PlayStateAction(UnrealLinkBundle.message("action.RiderLink.StopInUnrealAction.text"), UnrealLinkBundle.message("action.RiderLink.StopInUnrealAction.description"), UnrealIcons.Status.Stop) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost()?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState != PlayState.Idle
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost()?: return
        host.model.playStateFromRider.fire(PlayState.Idle)
    }
}

class PauseInUnrealAction : PlayStateAction(UnrealLinkBundle.message("action.RiderLink.PauseInUnrealAction.text"),
    UnrealLinkBundle.message("action.RiderLink.PauseInUnrealAction.description"), UnrealIcons.Status.Pause) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost()?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState != PlayState.Idle
        e.presentation.icon = if (host.playState == PlayState.Pause) UnrealIcons.Status.FrameSkip else UnrealIcons.Status.Pause
        e.presentation.text = if (host.playState == PlayState.Pause) UnrealLinkBundle.message("action.RiderLink.SkipFrame.text") else UnrealLinkBundle.message("action.RiderLink.PauseInUnrealAction.text")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        when(host.playState) {
            PlayState.Play -> host.model.playStateFromRider.fire(PlayState.Pause)
            PlayState.Pause -> host.model.frameSkip.fire()
            else -> host.logger.error("[UnrealLink] Invalid play state for PauseInUnrealAction: ${host.playState}")
        }
    }
}

class PlaySettings : DefaultActionGroup() {
    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = e.getHost()?.isUnrealEngineSolution?:false
        e.presentation.isEnabled = e.getHost()?.isConnectedToUnrealEditor?:false
    }
}

class PlaySubsettings : DefaultActionGroup() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor?:false
    }
}

class NumberOfPlayers : ToggleAction() {
    private fun setNumPlayers(mode: Int, num: Int): Int {
        return mode and 3.inv() or (num - 1)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getHost() ?: return false
        return (host.playMode and 3 + 1).toString() == e.presentation.text
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getHost() ?: return

        if(isSelected) {
            host.playMode = setNumPlayers(host.playMode, e.presentation.text.toInt(10))
            host.model.playModeFromRider.fire(host.playMode)
        }
    }
}

class SpawnPlayer : ToggleAction() {
    private fun getSpawnPlayerMode(text: String) = when(text) {
        UnrealLinkBundle.message("action.RiderLink.CurrentCamLoc.text") -> 0
        UnrealLinkBundle.message("action.RiderLink.DefaultPlayerStart.text") -> 1
        else -> 0
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getHost() ?: return false

        return (host.playMode and 4).shr(2) == getSpawnPlayerMode(e.presentation.text)
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getHost() ?: return

        if(isSelected) {
            host.playMode = (host.playMode and 4.inv()) or getSpawnPlayerMode(e.presentation.text).shl(2)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }
}

class DedicatedServer : ToggleAction() {
    private fun setDedicatedServer(mode: Int, enabled: Boolean): Int {
        return mode and 8.inv() or (if (enabled) 8 else 0)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getHost() ?: return false

        return (host.playMode and 8) != 0
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getHost() ?: return

        if(isSelected) {
            host.playMode = setDedicatedServer(host.playMode, isSelected)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }
}

class CompileBeforeRun : ToggleAction() {
    private fun setCompileBeforeRun(mode: Int, enabled: Boolean): Int {
        return mode and 128.inv() or (if (enabled) 128 else 0)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getHost() ?: return false

        return (host.playMode and 128) != 0
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getHost() ?: return

        if(isSelected) {
            host.playMode = setCompileBeforeRun(host.playMode, isSelected)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }
}

class PlayMode : ToggleAction() {
    private fun getModeIndex(playModeName: String) = when (playModeName) {
            UnrealLinkBundle.message("action.RiderLink.SelectedViewport.text") -> 0
            UnrealLinkBundle.message("action.RiderLink.MobilePreview.text") -> 1
            UnrealLinkBundle.message("action.RiderLink.NewEditorWindow.text") -> 2
            UnrealLinkBundle.message("action.RiderLink.VRPreview.text") -> 3
            UnrealLinkBundle.message("action.RiderLink.StandaloneGame.text") -> 4
            UnrealLinkBundle.message("action.RiderLink.Simulate.text") -> 5
            UnrealLinkBundle.message("action.RiderLink.VulkanPreview.text") -> 6
            else -> 0
        }

    private fun setPlayMode(mode: Int, playModeName: String): Int {
        val playModeIndex = getModeIndex(playModeName)
        return mode and (16 + 32 + 64).inv() or playModeIndex.shl(4)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getHost() ?: return false

        val ind = getModeIndex(e.presentation.text)
        return (host.playMode and (16 + 32 + 64)) == ind.shl(4)
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getHost() ?: return

        if(isSelected) {
            host.playMode = setPlayMode(host.playMode, e.presentation.text)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }
}

fun AnActionEvent.getHost(): UnrealHost? {
    val project = project?: return null
    return UnrealHost.getInstance(project)
}