package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.UnrealHost

class PlaySettings : DefaultActionGroup() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = e.getHost()?.isUnrealEngineSolution ?: false
        e.presentation.isEnabled = e.getHost()?.isConnectedToUnrealEditor ?: false
    }
}

class PlaySubsettings : DefaultActionGroup() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
    }
}

class NumberOfPlayers : ToggleAction() {
    private fun setNumPlayers(mode: Int, num: Int): Int {
        return mode and 3.inv() or (num - 1)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getHost() ?: return false
        return ((host.playMode and 3) + 1).toString() == e.presentation.text
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getHost() ?: return

        if (isSelected) {
            host.playMode = setNumPlayers(host.playMode, e.presentation.text.toInt(10))
            host.model.playModeFromRider.fire(host.playMode)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
    }
}

class SpawnPlayer : ToggleAction() {
    private fun getSpawnPlayerMode(text: String) = when (text) {
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

        if (isSelected) {
            host.playMode = (host.playMode and 4.inv()) or getSpawnPlayerMode(e.presentation.text).shl(2)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
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

        host.playMode = setDedicatedServer(host.playMode, isSelected)
        host.model.playModeFromRider.fire(host.playMode)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
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

        host.playMode = setCompileBeforeRun(host.playMode, isSelected)
        host.model.playModeFromRider.fire(host.playMode)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
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

        if (isSelected) {
            host.playMode = setPlayMode(host.playMode, e.presentation.text)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
    }
}