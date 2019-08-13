package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.StatusBarUtil
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelModel
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelView
import com.jetbrains.rider.plugins.unreal.UnrealHost

class UnrealToolWindowFactory(project: Project,
                              private val toolWindowManager: ToolWindowManager,
                              private val host: UnrealHost)
    : LifetimedProjectComponent(project) {

    companion object {
        val TOOLWINDOW_ID = "unreal"
        val ACTION_PLACE = "unreal"
    }

    private val lock = Object()
    private var context: UnrealToolWindowContext? = null

    fun getOrCreateContext(): UnrealToolWindowContext {
        synchronized(lock) {
            return context?:create()
        }
    }

    private fun create(): UnrealToolWindowContext {
        val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, project, true, false)

//        if (toolWindow is ToolWindowEx) {
//            toolWindow.setAdditionalGearActions(DefaultActionGroup().apply {
//                add(RiderUnityOpenEditorLogAction())
//                add(RiderUnityOpenPlayerLogAction())
//            })
//        }

        val contentManager = toolWindow.contentManager
        contentManager.addContentManagerListener(object : ContentManagerAdapter() {
            override fun contentRemoved(event: ContentManagerEvent) {
                context = null
                toolWindowManager.unregisterToolWindow(TOOLWINDOW_ID)
            }
        })
        toolWindow.title = ""
//        toolWindow.icon = UnityIcons.Toolwindows.ToolWindowUnityLog
        // Required for hiding window without content
        ContentManagerWatcher(toolWindow, contentManager)

        val logModel = UnrealLogPanelModel(componentLifetime, project)
        val logView = UnrealLogPanelView(project, logModel, host)
        val toolWindowContent = contentManager.factory.createContent(null, "Log", true).apply {
            StatusBarUtil.setStatusBarInfo(project, "")
            component = logView.panel
            isCloseable = false
        }

        contentManager.addContent(toolWindowContent)
        val twContext = UnrealToolWindowContext(toolWindow, logModel)
        context = twContext
        return twContext
    }
}