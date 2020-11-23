package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.util.SmartList
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rd.util.reactive.valueOrDefault
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

class PlayInUnrealAction : PlayStateAction("Play", "Play", UnrealIcons.Status.Play) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val value = e.getHost()?.playState
        e.presentation.isEnabled = e.presentation.isEnabled && value != PlayState.Play
        e.presentation.text = if (value == PlayState.Pause) "Resume Unreal" else "Play"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost()?:return
        host.model.playStateFromRider.fire(PlayState.Play)
    }
}

class StopInUnrealAction : PlayStateAction("Stop", "Stop", UnrealIcons.Status.Stop) {
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

class PauseInUnrealAction : PlayStateAction("Pause", "Pause", UnrealIcons.Status.Pause) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost()?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState != PlayState.Idle
        e.presentation.icon = if (host.playState == PlayState.Pause) UnrealIcons.Status.FrameSkip else UnrealIcons.Status.Pause
        e.presentation.text = if (host.playState == PlayState.Pause) "Frame Skip" else "Pause"
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

abstract class OnlyOneSelectedAction : ToggleAction() {
    protected abstract fun getActions(): SmartList<OnlyOneSelectedAction>
    protected abstract fun initialUpdate(e: AnActionEvent)

    protected var selected: Boolean = false
    private var firstUpdate: Boolean = true

    override fun isSelected(e: AnActionEvent): Boolean {
        return selected
    }

    override fun setSelected(e: AnActionEvent, value: Boolean) {
        for (action in getActions()) {
            action.selected = action == this
        }
    }
    override fun update(e: AnActionEvent) {
        super.update(e)
        if (firstUpdate) {
            firstUpdate = false
            initialUpdate(e)
        }
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor?:false
    }
}

class NumberOfPlayers : OnlyOneSelectedAction() {
    companion object {
        var allActions = SmartList<OnlyOneSelectedAction>()

        fun getNumPlayersAction(mode: Int): ToggleAction {
            return when (mode and 3) {
                1 -> ActionManager.getInstance().getAction("RiderLink.TwoPlayers") as ToggleAction
                2 -> ActionManager.getInstance().getAction("RiderLink.ThreePlayers") as ToggleAction
                3 -> ActionManager.getInstance().getAction("RiderLink.FourPlayers") as ToggleAction
                else -> ActionManager.getInstance().getAction("RiderLink.OnePlayer") as ToggleAction
            }
        }

        fun setNumPlayers(mode: Int, num: Int): Int {
            return mode and 3.inv() or (num - 1)
        }
    }

    override fun getActions(): SmartList<OnlyOneSelectedAction> {
        return allActions
    }

    override fun initialUpdate(e: AnActionEvent) {
        allActions.add(this)
        selected = e.presentation.text == "1"
    }

    override fun setSelected(e: AnActionEvent, value: Boolean) {
        super.setSelected(e, value)

        val host: UnrealHost? = e.getHost() ?: return
        var mode: Int = host!!.model.playMode.valueOrDefault(0)
        mode = setNumPlayers(mode, e.presentation.text.toInt(10))
        host.model.playMode.set(mode)
    }
}

class SpawnPlayer : OnlyOneSelectedAction() {
    companion object {
        var allActions = SmartList<OnlyOneSelectedAction>()

        fun getSpawnLocationAction(mode: Int): ToggleAction {
            return when (mode and 4) {
                0 -> ActionManager.getInstance().getAction("RiderLink.CurrentCamLoc") as ToggleAction
                else -> ActionManager.getInstance().getAction("RiderLink.DefaultPlayerStart") as ToggleAction
            }
        }

        fun setSpawnLocation(mode: Int, location: String): Int {
            return mode and 4.inv() or (if (location == UnrealLinkBundle.message("action.RiderLink.CurrentCamLoc.text")) 0 else 4)
        }
    }

    override fun getActions(): SmartList<OnlyOneSelectedAction> {
        return allActions
    }

    override fun initialUpdate(e: AnActionEvent) {
        allActions.add(this)
        selected = e.presentation.text == UnrealLinkBundle.message("action.RiderLink.CurrentCamLoc.text")
    }

    override fun setSelected(e: AnActionEvent, value: Boolean) {
        super.setSelected(e, value)

        val host: UnrealHost? = e.getHost() ?: return
        var mode: Int = host!!.model.playMode.valueOrDefault(0)
        mode = setSpawnLocation(mode, e.presentation.text)
        host.model.playMode.set(mode)
    }
}

class DedicatedServer : ToggleAction() {
    companion object {
        fun getDedicatedServer(mode: Int): Boolean {
            return ((mode shr 3) and 1) == 1
        }
    }

    var selected: Boolean = false
    fun setDedicatedServer(mode: Int, enabled: Boolean): Int {
        return mode and 8.inv() or (if (enabled) 8 else 0)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        return selected
    }

    override fun setSelected(e: AnActionEvent, value: Boolean) {
        selected = value
        val host: UnrealHost? = e.getHost() ?: return
        var mode: Int = host!!.model.playMode.valueOrDefault(0)
        mode = setDedicatedServer(mode, value)
        host.model.playMode.set(mode)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor?:false
    }
}

class CompileBeforeRun : ToggleAction() {
    var selected: Boolean = false
    fun setCompileBeforeRun(mode: Int, enabled: Boolean): Int {
        return mode and 128.inv() or (if (enabled) 128 else 0)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        return selected
    }

    override fun setSelected(e: AnActionEvent, value: Boolean) {
        selected = value
        val host: UnrealHost? = e.getHost() ?: return
        var mode: Int = host!!.model.playMode.valueOrDefault(0)
        mode = setCompileBeforeRun(mode, value)
        host.model.playMode.set(mode)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor?:false
    }
}

class PlayMode : OnlyOneSelectedAction() {
    companion object {
        var allActions = SmartList<OnlyOneSelectedAction>()

        fun getPlayModeAction(mode: Int): ToggleAction {
            return when (mode.shr(4) and 7) {
                1 -> ActionManager.getInstance().getAction("RiderLink.MobilePreview") as ToggleAction
                2 -> ActionManager.getInstance().getAction("RiderLink.NewEditorWindow") as ToggleAction
                3 -> ActionManager.getInstance().getAction("RiderLink.VRPreview") as ToggleAction
                4 -> ActionManager.getInstance().getAction("RiderLink.StandaloneGame") as ToggleAction
                5 -> ActionManager.getInstance().getAction("RiderLink.Simulate") as ToggleAction
                6 -> ActionManager.getInstance().getAction("RiderLink.VulkanPreview") as ToggleAction
                else -> ActionManager.getInstance().getAction("RiderLink.SelectedViewport") as ToggleAction
            }
        }

        fun setPlayMode(mode: Int, playModeName: String): Int {
            var playModeIndex: Int = 0
            when (playModeName) {
                UnrealLinkBundle.message("action.RiderLink.SelectedViewport.text") -> playModeIndex = 0
                UnrealLinkBundle.message("action.RiderLink.MobilePreview.text") -> playModeIndex = 1
                UnrealLinkBundle.message("action.RiderLink.NewEditorWindow.text") -> playModeIndex = 2
                UnrealLinkBundle.message("action.RiderLink.VRPreview.text") -> playModeIndex = 3
                UnrealLinkBundle.message("action.RiderLink.StandaloneGame.text") -> playModeIndex = 4
                UnrealLinkBundle.message("action.RiderLink.Simulate.text") -> playModeIndex = 5
                UnrealLinkBundle.message("action.RiderLink.VulkanPreview.text") -> playModeIndex = 6
            }
            return mode and (16 + 32 + 64).inv() or playModeIndex.shl(4)
        }
    }

    override fun getActions(): SmartList<OnlyOneSelectedAction> {
        return allActions
    }

    override fun initialUpdate(e: AnActionEvent) {
        allActions.add(this)
        selected = e.presentation.text == UnrealLinkBundle.message("action.RiderLink.SelectedViewport.text")
    }

    override fun setSelected(e: AnActionEvent, value: Boolean) {
        super.setSelected(e, value)

        val host: UnrealHost? = e.getHost() ?: return
        var mode: Int = host!!.model.playMode.valueOrDefault(0)
        mode = setPlayMode(mode, e.presentation.text)
        host.model.playMode.set(mode)
    }
}

fun AnActionEvent.    getHost(): UnrealHost? {
    val project = project?: return null
    return UnrealHost.getInstance(project)
}