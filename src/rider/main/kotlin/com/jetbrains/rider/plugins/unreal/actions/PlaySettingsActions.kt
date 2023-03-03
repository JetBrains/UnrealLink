package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.xdebugger.attach.LocalAttachHost
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.cpp.debugger.RiderCppLLDBDriverConfiguration
import com.jetbrains.rider.cpp.debugger.RiderCppLocalAttachDebugger
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings
import com.jetbrains.rider.settings.UnrealLogSettingsConfigurable
import icons.UnrealIcons

class PlaySettings : DefaultActionGroup(), DumbAware {
    private val connectedIcon = UnrealIcons.Status.UnrealEngineConnected
    private val disconnectedIcon = UnrealIcons.Status.UnrealEngineDisconnected

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getUnrealHost()
        if (host == null || !host.isUnrealEngineSolution) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabled = true
        e.presentation.isVisible = true
        e.presentation.icon = if (host.isConnectedToUnrealEditor) connectedIcon else disconnectedIcon
    }
}

class ProtocolStatus : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) = Unit

    override fun update(e: AnActionEvent) {
        super.update(e)

        e.presentation.isEnabled = false

        val host = e.getUnrealHost() ?: return
        e.presentation.isVisible = true
        e.presentation.text = getActionText(host)
    }

    @NlsActions.ActionText
    fun getActionText(host: UnrealHost): String {
        return if (host.isConnectedToUnrealEditor) {
            val info = host.connectionInfo
            if (info != null) {
                UnrealLinkBundle.message("action.RiderLink.ProtocolStatus.connected.ex.text",
                        info.projectName, info.executableName, info.processId.toString())
            } else {
                UnrealLinkBundle.message("action.RiderLink.ProtocolStatus.connected.text")
            }
        } else {
            UnrealLinkBundle.message("action.RiderLink.ProtocolStatus.disconnected.text")
        }
    }
}

class AttachToConnectedEditor : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        super.update(e)

        val host = e.getUnrealHost()
        if (host == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isVisible = true

        if (!host.isConnectedToUnrealEditor) {
            e.presentation.isEnabled = false
            return
        }

        val connectionInfo = host.connectionInfo
        if (connectionInfo == null) {
            e.presentation.isEnabled = false
            return
        }

        // try to enumerate current debug sessions and find if we have already a debugger attached
        /*
        val debuggerManager = XDebuggerManager.getInstance(host.project)
        for (session in debuggerManager.debugSessions) {
            val debugProcess = session.debugProcess
            if (debugProcess is CidrDebugProcess) {
                // TODO: need to retrieve target pid from process
                val pid = 0 // debugProcess.processHandler
                if (pid == connectionInfo.processId) {
                    e.presentation.isEnabled = false
                    return
                }
            }
        }
        */

        e.presentation.isEnabled = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getUnrealHost() ?: return
        val connectionInfo = host.connectionInfo ?: return

        val processInfo = ProcessInfo(connectionInfo.processId, "", connectionInfo.executableName, "")
        attachToUnrealProcess(host.project, processInfo)
    }

    private fun attachToUnrealProcess(project: Project, processInfo: ProcessInfo) {
        val attachDebugger = RiderCppLocalAttachDebugger(RiderCppLLDBDriverConfiguration())
        attachDebugger.attachDebugSession(project, LocalAttachHost.INSTANCE, processInfo)
    }

}

class OpenUnrealLinkSettings : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val host = e.getUnrealHost()
        e.presentation.isEnabledAndVisible = host != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, UnrealLogSettingsConfigurable.UNREAL_LINK)
    }
}

class OpenRiderLinkSettings : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val host = e.getUnrealHost()
        e.presentation.isEnabledAndVisible = host != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "UnrealLinkOptionsId")
    }
}

private fun updatePlayActionPresentation(e: AnActionEvent) {
    val host = e.getUnrealHost()
    if (host == null) {
        e.presentation.isEnabledAndVisible = false
        return
    }
    val settings = UnrealLogPanelSettings.getInstance(host.project)
    e.presentation.isEnabled = host.isConnectedToUnrealEditor
    e.presentation.isVisible = settings.showPlayButtons
}

class PlaySubsettings : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)

        updatePlayActionPresentation(e)
    }
}

class NumberOfPlayers : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun setNumPlayers(mode: Int, num: Int): Int {
        return mode and 3.inv() or (num - 1)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getUnrealHost() ?: return false
        return ((host.playMode and 3) + 1).toString() == e.presentation.text
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getUnrealHost() ?: return

        if (isSelected) {
            host.playMode = setNumPlayers(host.playMode, e.presentation.text.toInt(10))
            host.model.playModeFromRider.fire(host.playMode)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        updatePlayActionPresentation(e)
    }
}

class SpawnPlayer : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getSpawnPlayerMode(text: String?) = when (text) {
        UnrealLinkBundle.message("action.RiderLink.CurrentCamLoc.text") -> 0
        UnrealLinkBundle.message("action.RiderLink.DefaultPlayerStart.text") -> 1
        else -> -1
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getUnrealHost() ?: return false

        val actionID = getSpawnPlayerMode(e.presentation.text)
        if (actionID == -1) return false

        return (host.playMode and 4).shr(2) == actionID
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getUnrealHost() ?: return

        if (isSelected) {
            val actionID = getSpawnPlayerMode(e.presentation.text)
            if (actionID != -1) return

            host.playMode = (host.playMode and 4.inv()) or actionID.shl(2)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        updatePlayActionPresentation(e)
    }
}

class DedicatedServer : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun setDedicatedServer(mode: Int, enabled: Boolean): Int {
        return mode and 8.inv() or (if (enabled) 8 else 0)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getUnrealHost() ?: return false

        return (host.playMode and 8) != 0
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getUnrealHost() ?: return

        host.playMode = setDedicatedServer(host.playMode, isSelected)
        host.model.playModeFromRider.fire(host.playMode)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        updatePlayActionPresentation(e)
    }
}

class CompileBeforeRun : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun setCompileBeforeRun(mode: Int, enabled: Boolean): Int {
        return mode and 128.inv() or (if (enabled) 128 else 0)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getUnrealHost() ?: return false

        return (host.playMode and 128) != 0
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getUnrealHost() ?: return

        host.playMode = setCompileBeforeRun(host.playMode, isSelected)
        host.model.playModeFromRider.fire(host.playMode)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        updatePlayActionPresentation(e)
    }
}

const val PLAY_MODE_MASK_OFS = 4
const val PLAY_MODE_MASK_SIZE = 3
const val PLAY_MODE_MASK = (1.shl(PLAY_MODE_MASK_SIZE) - 1).shl(PLAY_MODE_MASK_OFS)
const val PLAY_MODE_MASK_INV = PLAY_MODE_MASK.inv()

class PlayMode : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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

    private fun getPlayModeIndex(mode: Int) = (mode and PLAY_MODE_MASK).ushr(PLAY_MODE_MASK_OFS)

    private fun setPlayMode(mode: Int, playModeIndex: Int): Int {
        return (mode and PLAY_MODE_MASK_INV) or playModeIndex.shl(PLAY_MODE_MASK_OFS)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val host: UnrealHost = e.getUnrealHost() ?: return false

        val ind = getModeIndex(e.presentation.text)
        if (ind == -1) return false
        return getPlayModeIndex(host.playMode) == ind
    }

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        val host: UnrealHost = e.getUnrealHost() ?: return

        if (isSelected) {
            val playModeIndex = getModeIndex(e.presentation.text)
            if (playModeIndex == -1) return

            host.playMode = setPlayMode(host.playMode, playModeIndex)
            host.model.playModeFromRider.fire(host.playMode)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        updatePlayActionPresentation(e)
    }
}

class HidePlayButtonsAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)

        val host = e.getUnrealHost()
        e.presentation.isEnabledAndVisible = host != null
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val settings = UnrealLogPanelSettings.getInstance(project)
        return settings.showPlayButtons
    }

    override fun setSelected(e: AnActionEvent, selected: Boolean) {
        val project = e.project ?: return
        val settings = UnrealLogPanelSettings.getInstance(project)
        settings.showPlayButtons = selected
    }
}