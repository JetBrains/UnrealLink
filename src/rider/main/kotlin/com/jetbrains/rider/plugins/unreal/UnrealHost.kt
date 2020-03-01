package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rdclient.util.idea.getLogger
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.util.idea.getComponent
import com.sun.jna.LastErrorException
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary

class UnrealHost(project: Project) : LifetimedProjectComponent(project) {
    companion object {
        val logger = getLogger<UnrealHost>()
        fun getInstance(project: Project) = project.getComponent<UnrealHost>()
    }

    internal val model = project.solution.rdRiderModel

    init {
        model.allowSetForegroundWindow.set { _, id ->
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
