package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.reactive.adviseNotNull
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.actions.forceTriggerUIUpdate
import com.jetbrains.rider.plugins.unreal.model.PlayState
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
class UnrealHostSetup(project: Project) : LifetimedProjectComponent(project) {
    var isUnrealEngineSolution = false
    var isPreBuiltEngine = false

    init {
        val unrealHost = UnrealHost.getInstance(project)

        unrealHost.performModelAction {
            it.allowSetForegroundWindow.set { _, id ->
                if (SystemInfo.isWindows) {
                    return@set if (!user32!!.AllowSetForegroundWindow(id)) {
                        val lastError = kernel32!!.GetLastError()
                        RdTask.faulted(LastErrorException(lastError))
                    } else {
                        RdTask.fromResult(true)
                    }
                }
                RdTask.fromResult(true)
            }
        }

        unrealHost.performModelAction {
            it.isUnrealEngineSolution.change.advise(project.lifetime) { isUnrealEngineSolution ->
                this.isUnrealEngineSolution = isUnrealEngineSolution
                project.getService(StatusBarWidgetsManager::class.java).updateWidget(UnrealStatusBarWidget::class.java)
            }
            it.isPreBuiltEngine.change.advise(project.lifetime) { isPreBuiltEngine ->
                this.isPreBuiltEngine = isPreBuiltEngine
            }
        }

        unrealHost.performModelAction {
            it.isConnectedToUnrealEditor.change.advise(project.lifetime) { connected ->
                if (!connected) {
                    unrealHost.playStateModel.set(PlayState.Idle)
                }
            }
        }

        unrealHost.performModelAction {
            it.riderLinkInstallPanelInit.advise(project.lifetime) {
                val riderLinkInstallContext =
                        RiderLinkInstallService.getInstance(project).getOrCreateRiderLinkInstallContext()
                riderLinkInstallContext.clear()
                riderLinkInstallContext.showToolWindowIfHidden()
            }
            it.riderLinkInstallMessage.advise(project.lifetime) { message ->
                RiderLinkInstallService.getInstance(project).getOrCreateRiderLinkInstallContext().writeMessage(message)
            }
        }

//  Update state of Unreal actions on toolbar
        unrealHost.performModelAction {
            it.playStateFromEditor.adviseNotNull(project.lifetime) { newState ->
                unrealHost.playStateModel.set(newState)
            }
        }

        unrealHost.performModelAction {
            it.playModeFromEditor.adviseNotNull(project.lifetime) { mode ->
                unrealHost.playMode = mode
                forceTriggerUIUpdate()
            }
        }

        unrealHost.performModelAction {
            it.isGameControlModuleInitialized.adviseNotNull(project.lifetime) {
                forceTriggerUIUpdate()
            }
        }
    }

    private val user32 = if (SystemInfo.isWindows) Native.load("user32", User32::class.java) else null
    private val kernel32 = if (SystemInfo.isWindows) Native.load("kernel32", Kernel32::class.java) else null

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