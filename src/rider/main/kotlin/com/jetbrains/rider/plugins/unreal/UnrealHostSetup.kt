package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.rd.util.lifetime
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.protocol.SolutionExtListener
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseNotNull
import com.jetbrains.rider.plugins.unreal.actions.forceTriggerUIUpdate
import com.jetbrains.rider.plugins.unreal.model.PlayState
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.RdRiderModel
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
@Service(Service.Level.PROJECT)
class UnrealHostSetup {
    var isUnrealEngineSolution = false
    var isPreBuiltEngine = false

    class ProtocolListener : SolutionExtListener<RdRiderModel> {
        override fun extensionCreated(lifetime: Lifetime, session: ClientProjectSession, model: RdRiderModel) {
            model.allowSetForegroundWindow.set { _, id ->
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

            val project = session.project
            model.isUnrealEngineSolution.change.advise(lifetime) { isUnrealEngineSolution ->
                val host = project.service<UnrealHostSetup>()
                host.isUnrealEngineSolution = isUnrealEngineSolution
            }
            model.isPreBuiltEngine.change.advise(project.lifetime) { isPreBuiltEngine ->
                val host = project.service<UnrealHostSetup>()
                host.isPreBuiltEngine = isPreBuiltEngine
            }


            model.isConnectedToUnrealEditor.change.advise(project.lifetime) { connected ->
                if (!connected) {
                    project.service<UnrealHost>().playStateModel.set(PlayState.Idle)
                }
                forceTriggerUIUpdate()
            }


            model.riderLinkInstallPanelInit.advise(project.lifetime) {
                val riderLinkInstallContext =
                    RiderLinkInstallService.getInstance(project).getOrCreateRiderLinkInstallContext()
                riderLinkInstallContext.clear()
                riderLinkInstallContext.showToolWindowIfHidden()
            }
            model.riderLinkInstallMessage.advise(project.lifetime) { message ->
                RiderLinkInstallService.getInstance(project).getOrCreateRiderLinkInstallContext().writeMessage(message)
            }

//  Update state of Unreal actions on toolbar
            model.playStateFromEditor.adviseNotNull(project.lifetime) { newState ->
                project.service<UnrealHost>().playStateModel.set(newState)
            }

            model.playModeFromEditor.adviseNotNull(project.lifetime) { mode ->
                project.service<UnrealHost>().playMode = mode
                forceTriggerUIUpdate()
            }

            model.isGameControlModuleInitialized.adviseNotNull(project.lifetime) {
                forceTriggerUIUpdate()
            }
        }

        private val user32 = if (SystemInfo.isWindows) Native.load("user32", User32::class.java) else null
        private val kernel32 = if (SystemInfo.isWindows) Native.load("kernel32", Kernel32::class.java) else null

        @Suppress("FunctionName")
        private interface User32 : StdCallLibrary {
            fun AllowSetForegroundWindow(id: Int): Boolean

            @Suppress("unused")
            fun SetForegroundWindow(hwnd: WinDef.HWND): Boolean

            @Suppress("unused")
            fun EnumWindows(callback: EnumWindowsProc, intPtr: WinDef.INT_PTR): Boolean

            @Suppress("unused")
            fun GetWindowThreadProcessId(hwnd: WinDef.HWND, processId: WinDef.UINTByReference): Boolean

            @Suppress("unused")
            fun ShowWindow(hwnd: WinDef.HWND, nCmdShow: Int): Boolean
        }

        @Suppress("FunctionName")
        private interface Kernel32 : StdCallLibrary {
            fun GetLastError(): Int
        }

        private interface EnumWindowsProc : StdCallLibrary.StdCallCallback {
            @Suppress("unused")
            fun callback(hwnd: WinDef.HWND, intPtr: WinDef.INT_PTR): Boolean
        }
    }
}