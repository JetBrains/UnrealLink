package com.jetbrains.rider.plugins.unreal

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.actions.DedicatedServer
import com.jetbrains.rider.plugins.unreal.actions.NumberOfPlayers
import com.jetbrains.rider.plugins.unreal.actions.PlayMode
import com.jetbrains.rider.plugins.unreal.actions.SpawnPlayer
import com.jetbrains.rider.plugins.unreal.ui.UnrealStatusBarWidget
import com.sun.jna.LastErrorException
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary

// Q: Why separate UnrealHostSetup and UnrealHost?
// A: Some classes need to get access to UnrealHost on `advise` methods. If we'd setup this in UnrealHost, we'll get a circular dependency
// Example:
// UnrealHost.init -> UnrealHost.model.isUnrealEngineSolution.advise -> StatusBarWidgetsManager.updateWidget -> UnrealStatusBarWidget.update ->
// UnrealStatusBarWidget.isAvailable -> UnrealHost.model.isUnrealEngineSolution.value -> UnrealHost.init
// We need to finish `init` first, then subscribe to changes in model
class UnrealHostSetup(project: Project, unrealHost: UnrealHost) : LifetimedProjectComponent(project) {
    init {
        unrealHost.performModelAction {
            it.allowSetForegroundWindow.set { _, id ->
                if (SystemInfo.isWindows) {
                    return@set if (!user32.AllowSetForegroundWindow(id)) {
                        val lastError = kernel32.GetLastError()
                        RdTask.faulted(LastErrorException(lastError))
                    } else {
                        RdTask.fromResult(true)
                    }
                }
                RdTask.fromResult(true)
            }
        }

        unrealHost.performModelAction {
            it.isUnrealEngineSolution.change.advise(project.lifetime) {
                project.getService(StatusBarWidgetsManager::class.java).updateWidget(UnrealStatusBarWidget::class.java)
            }
        }

        unrealHost.performModelAction {
            it.playMode.advise(project.lifetime) { mode ->
                val numPlayersAction = NumberOfPlayers.getNumPlayersAction(mode)
                val spawnAtPlayerStartAction = SpawnPlayer.getSpawnLocationAction(mode)
                val playModeAction = PlayMode.getPlayModeAction(mode)
                val dedicatedServerAction = ActionManager.getInstance().getAction("RiderLink.DedicatedServer") as ToggleAction

                numPlayersAction.setSelected(AnActionEvent(null, DataManager.getInstance().dataContext, "", numPlayersAction.templatePresentation, ActionManager.getInstance(), 0), true)
                spawnAtPlayerStartAction.setSelected(AnActionEvent(null, DataManager.getInstance().dataContext, "", spawnAtPlayerStartAction.templatePresentation, ActionManager.getInstance(), 0), true)
                playModeAction.setSelected(AnActionEvent(null, DataManager.getInstance().dataContext, "", playModeAction.templatePresentation, ActionManager.getInstance(), 0), true)
                dedicatedServerAction.setSelected(AnActionEvent(null, DataManager.getInstance().dataContext, "", dedicatedServerAction.templatePresentation, ActionManager.getInstance(), 0), DedicatedServer.getDedicatedServer(mode))
            }
        }
    }

    private val user32 = Native.load("user32", User32::class.java)
    private val kernel32 = Native.load("kernel32", Kernel32::class.java)

    @Suppress("FunctionName")
    private interface User32 : StdCallLibrary {
        fun AllowSetForegroundWindow(id: Int): Boolean

        fun SetForegroundWindow(hwnd: WinDef.HWND): Boolean

        fun EnumWindows(callback: EnumWindowsProc, intPtr: WinDef.INT_PTR): Boolean

        fun GetWindowThreadProcessId(hwnd: WinDef.HWND, processId: WinDef.UINTByReference): Boolean

        fun ShowWindow(hwnd: WinDef.HWND, nCmdShow: Int): Boolean
    }

    @Suppress("FunctionName")
    private interface Kernel32 : StdCallLibrary {
        fun GetLastError(): Int
    }

    @Suppress("FunctionName")
    private interface EnumWindowsProc : StdCallLibrary.StdCallCallback {
        fun callback(hwnd: WinDef.HWND, intPtr: WinDef.INT_PTR): Boolean
    }
}