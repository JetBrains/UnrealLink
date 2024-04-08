package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.framework.protocolOrThrow
import com.jetbrains.rd.protocol.SolutionExtListener
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.adviseNotNull
import com.jetbrains.rd.util.reactive.whenTrue
import com.jetbrains.rider.plugins.unreal.actions.forceTriggerUIUpdate
import com.jetbrains.rider.plugins.unreal.model.ConnectionInfo
import com.jetbrains.rider.plugins.unreal.model.PlayState
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.RdRiderModel
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.plugins.unreal.toolWindow.UnrealToolWindowFactory
import com.jetbrains.rider.projectView.solution
import com.sun.jna.LastErrorException
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary

@Service(Service.Level.PROJECT)
class UnrealHost(val project: Project) {
    companion object {
        fun getInstance(project:Project) = project.service<UnrealHost>()
    }
    val logger = Logger.getInstance(UnrealHost::class.java)

    internal val playStateModel: IProperty<PlayState> = Property(PlayState.Idle)
    val playState: PlayState
        get() = playStateModel.value
    var playMode: Int = 0

    internal val model = project.solution.rdRiderModel
    val isConnectedToUnrealEditor:Boolean
        get() = model.isConnectedToUnrealEditor.value
    val connectionInfo:ConnectionInfo?
        get() = model.connectionInfo.valueOrNull
    val isRiderLinkInstallationInProgress:Boolean
        get() = model.riderLinkInstallationInProgress.value
    val isRefreshProjectsInProgress:Boolean
        get() = model.refreshInProgress.value
    val isUproject:Boolean
        get() = model.isUproject.value
    val isInstallInfoAvailable:Boolean
        get() = model.isInstallInfoAvailable.value
    val isHotReloadAvailable: Boolean
        get() = model.isHotReloadAvailable.value
    val isHotReloadCompiling: Boolean
        get() = model.isHotReloadCompiling.value


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
                val host = project.service<UnrealHost>()
                host.isUnrealEngineSolution = isUnrealEngineSolution
            }
            model.isPreBuiltEngine.change.advise(lifetime) { isPreBuiltEngine ->
                val host = project.service<UnrealHost>()
                host.isPreBuiltEngine = isPreBuiltEngine
            }

            model.isConnectedToUnrealEditor.change.advise(lifetime) { connected ->
                if (!connected) {
                    project.service<UnrealHost>().playStateModel.set(PlayState.Idle)
                }
                forceTriggerUIUpdate()
            }

            model.riderLinkInstallPanelInit.advise(lifetime) {
                val riderLinkInstallContext =
                    RiderLinkInstallService.getInstance(project).getOrCreateRiderLinkInstallContext()
                riderLinkInstallContext.clear()
                riderLinkInstallContext.showToolWindowIfHidden()
            }

            model.riderLinkInstallMessage.advise(lifetime) { message ->
                RiderLinkInstallService.getInstance(project).getOrCreateRiderLinkInstallContext().writeMessage(message)
            }

//  Update state of Unreal actions on toolbar
            model.playStateFromEditor.adviseNotNull(lifetime) { newState ->
                project.service<UnrealHost>().playStateModel.set(newState)
            }

            model.playModeFromEditor.adviseNotNull(lifetime) { mode ->
                project.service<UnrealHost>().playMode = mode
                forceTriggerUIUpdate()
            }

            model.isGameControlModuleInitialized.adviseNotNull(lifetime) {
                forceTriggerUIUpdate()
            }

            model.isConnectedToUnrealEditor.whenTrue(lifetime) {
                val toolWindowsFactory = UnrealToolWindowFactory.getInstance(project)
                toolWindowsFactory.showTabForNewSession()
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

    fun <R> performModelAction(action:(RdRiderModel)->R) {
        model.protocolOrThrow.scheduler.invokeOrQueue {
            action(model)
        }
    }
}
