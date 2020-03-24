package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.util.SmartList
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.reactive.valueOrDefault
import com.jetbrains.rider.plugins.unreal.UnrealHost

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
            return mode and 4.inv() or (if (location == "Current Camera Location") 0 else 4)
        }
    }

    override fun getActions(): SmartList<OnlyOneSelectedAction> {
        return allActions
    }

    override fun initialUpdate(e: AnActionEvent) {
        allActions.add(this)
        selected = e.presentation.text == "Current Camera Location"
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
                "Selected Viewport" -> playModeIndex = 0
                "Mobile Preview" -> playModeIndex = 1
                "New Editor Window" -> playModeIndex = 2
                "VR Preview" -> playModeIndex = 3
                "Standalone Game" -> playModeIndex = 4
                "Simulate" -> playModeIndex = 5
                "Vulkan Preview" -> playModeIndex = 6
            }
            return mode and (16 + 32 + 64).inv() or playModeIndex.shl(4)
        }
    }

    override fun getActions(): SmartList<OnlyOneSelectedAction> {
        return allActions
    }

    override fun initialUpdate(e: AnActionEvent) {
        allActions.add(this)
        selected = e.presentation.text == "Selected Viewport"
    }

    override fun setSelected(e: AnActionEvent, value: Boolean) {
        super.setSelected(e, value)

        val host: UnrealHost? = e.getHost() ?: return
        var mode: Int = host!!.model.playMode.valueOrDefault(0)
        mode = setPlayMode(mode, e.presentation.text)
        host.model.playMode.set(mode)
    }
}

fun AnActionEvent.getHost(): UnrealHost? {
    val project = project?: return null
    return UnrealHost.getInstance(project)
}