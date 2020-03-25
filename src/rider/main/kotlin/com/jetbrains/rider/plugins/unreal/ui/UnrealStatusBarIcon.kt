package com.jetbrains.rider.plugins.unreal.ui

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Consumer
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.reactive.valueOrDefault
import com.jetbrains.rider.plugins.unreal.UnrealHost
import icons.UnrealIcons
import java.awt.event.MouseEvent
import javax.swing.Icon

class UnrealStatusBarIcon(project: Project): StatusBarWidget, StatusBarWidget.IconPresentation {
    companion object {
        const val StatusBarIconId = "UnrealStatusIcon"
    }

    private val host = UnrealHost.getInstance(project)

    init {
        host.model.isConnectedToUnrealEditor.advise(project.lifetime) {
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            statusBar.updateWidget(StatusBarIconId)
        }
    }

    private val connectedIcon = ExecutionUtil.getLiveIndicator(UnrealIcons.Status.Connected)
    private val disconnectedIcon = ExecutionUtil.getLiveIndicator(UnrealIcons.Status.Disconnected)
    private var myStatusBar: StatusBar? = null

    override fun ID(): String {
        return StatusBarIconId
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation? {
        return this
    }

    override fun install(statusBar: StatusBar) {
        myStatusBar = statusBar
    }

    override fun dispose() {
        myStatusBar = null
    }

    override fun getTooltipText(): String? {
        return if(host.model.isConnectedToUnrealEditor.value)
            "Connected to Unreal Editor"
        else
            "No Unreal Editor connection\nLoad the project in the Unreal Editor to enable advanced functionality"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    override fun getIcon(): Icon {
        return if (host.model.isConnectedToUnrealEditor.value) connectedIcon else disconnectedIcon
    }
}
