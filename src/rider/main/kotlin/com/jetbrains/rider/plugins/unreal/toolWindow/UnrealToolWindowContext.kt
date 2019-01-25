package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.openapi.wm.ToolWindow
//import com.jetbrains.rider.plugins.unity.editorPlugin.model.*
import com.jetbrains.rider.plugins.unity.toolWindow.log.UnrealLogPanelModel

class UnrealToolWindowContext(private val toolWindow: ToolWindow,
                              private val logModel: UnrealLogPanelModel) {

    fun activateToolWindowIfNotActive() {
        if (!(toolWindow.isActive)) {
            toolWindow.activate {}
        }
    }

    val isActive get() = toolWindow.isActive

    fun addEvent(event: String) = logModel.events.addEvent(event)
}