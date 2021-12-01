package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.UnrealHost

class PlaySettings : DefaultActionGroup(), DumbAware {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = e.getHost()?.isUnrealEngineSolution ?: false
        e.presentation.isEnabled = e.getHost()?.isConnectedToUnrealEditor ?: false
    }
}

class PlaySubsettings : DefaultActionGroup(), DumbAware {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
    }
}

class NumberOfPlayers : DumbAwareToggleAction() {
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

class SpawnPlayer : DumbAwareToggleAction() {
    private fun getSpawnPlayerMode(text: String?) = when (text) {
        UnrealLinkBundle.message("action.RiderLink.CurrentCamLoc.text") -> 0
        UnrealLinkBundle.message("action.RiderLink.DefaultPlayerStart.text") -> 1
        else -> -1
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getHost() ?: return false

        val actionID = getSpawnPlayerMode(e.presentation.text)
        if (actionID == -1) return false

        return (host.playMode and 4).shr(2) == actionID
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getHost() ?: return

        if (isSelected) {
            val actionID = getSpawnPlayerMode(e.presentation.text)
            if (actionID != -1) return

            host.playMode = (host.playMode and 4.inv()) or actionID.shl(2)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
    }
}

class DedicatedServer : DumbAwareToggleAction() {
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

class CompileBeforeRun : DumbAwareToggleAction() {
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

const val PLAY_MODE_MASK_OFS = 4
const val PLAY_MODE_MASK_SIZE = 3
const val PLAY_MODE_MASK = (1.shl(PLAY_MODE_MASK_SIZE) - 1).shl(PLAY_MODE_MASK_OFS)
const val PLAY_MODE_MASK_INV = PLAY_MODE_MASK.inv()

class PlayMode : DumbAwareToggleAction() {
    private fun getModeIndex(playModeName: String?) = when (playModeName) {
        UnrealLinkBundle.message("action.RiderLink.SelectedViewport.text") -> 0
        UnrealLinkBundle.message("action.RiderLink.MobilePreview.text") -> 1
        UnrealLinkBundle.message("action.RiderLink.NewEditorWindow.text") -> 2
        UnrealLinkBundle.message("action.RiderLink.VRPreview.text") -> 3
        UnrealLinkBundle.message("action.RiderLink.StandaloneGame.text") -> 4
        UnrealLinkBundle.message("action.RiderLink.Simulate.text") -> 5
        UnrealLinkBundle.message("action.RiderLink.VulkanPreview.text") -> 6
        else -> -1
    }

    private fun getPlayModeIndex(mode : Int) = (mode and PLAY_MODE_MASK).ushr(PLAY_MODE_MASK_OFS)

    private fun setPlayMode(mode: Int, playModeIndex: Int): Int {
        return (mode and PLAY_MODE_MASK_INV) or playModeIndex.shl(PLAY_MODE_MASK_OFS)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getHost() ?: return false

        val ind = getModeIndex(e.presentation.text)
        if (ind == -1) return false
        return getPlayModeIndex(host.playMode) == ind
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getHost() ?: return

        if (isSelected) {
            val playModeIndex = getModeIndex(e.presentation.text)
            if (playModeIndex == -1) return

            host.playMode = setPlayMode(host.playMode, playModeIndex)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getHost()?.isConnectedToUnrealEditor ?: false
    }
}