package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanel
import com.jetbrains.rider.ui.toolWindow.RiderOnDemandToolWindowFactory
import icons.RiderIcons

@Service
class UnrealToolWindowFactory(val project: Project)
    : RiderOnDemandToolWindowFactory<String>(project, TOOLWINDOW_ID, { it }, ::UnrealLogPanel, { it }) {

    companion object {
        const val TOOLWINDOW_ID = "Unreal"
        const val TITLE_ID = "Unreal Editor Log"
        const val ACTION_PLACE = "unreal"

        fun getInstance(project: Project): UnrealToolWindowFactory = project.service()
    }

    override fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(
                RegisterToolWindowTask(id = TOOLWINDOW_ID, anchor = ToolWindowAnchor.BOTTOM,
                        icon = RiderIcons.Stacktrace.Stacktrace, // TODO: change this placeholder to proper icon
                        canCloseContent = false, canWorkInDumbMode = true, sideTool = false)
        )
        toolWindow.title = UnrealLinkBundle.message("toolWindow.UnrealLog.title")

        val contentManager = toolWindow.contentManager
        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) {
                // hacky way to fix memory leaks on exit
                val content = event.content.component as UnrealLogPanel
                Disposer.register(toolWindow.disposable, content.console)
            }
        })

        ContentManagerWatcher.watchContentManager(toolWindow, contentManager)

        return toolWindow
    }

    fun showTabForNewSession() {
        ToolWindowManager.getInstance(project).invokeLater {
            showTab(TITLE_ID, project.lifetime)
        }
    }
}
