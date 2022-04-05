package com.jetbrains.rider.plugins.unreal.toolWindow

import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanel
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings
import icons.RiderIcons

@Service
class UnrealToolWindowFactory(val project: Project) {

    companion object {
        const val TOOLWINDOW_ID = "Unreal"
        const val TITLE_ID = "Unreal Editor Log"
        const val ACTION_PLACE = "unreal"

        fun getInstance(project: Project): UnrealToolWindowFactory = project.service()
    }

    private val contentMap: HashMap<String, Content> = HashMap()
    private val contentManager by lazy { getToolWindow(project).contentManager }

    private fun registerToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
        val toolWindow = toolWindowManager.registerToolWindow(
            RegisterToolWindowTask(
                id = TOOLWINDOW_ID, anchor = ToolWindowAnchor.BOTTOM,
                icon = RiderIcons.Stacktrace.Stacktrace, // TODO: change this placeholder to proper icon
                canCloseContent = false, canWorkInDumbMode = true, sideTool = false
            )
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
            val settings = project.service<UnrealLogPanelSettings>()
            if (settings.focusOnStart) {
                showTab(TITLE_ID, project.lifetime)
            } else {
                getOrCreateTab(TITLE_ID, project.lifetime)
            }
        }
    }

    private fun showTab(title: String, lt: Lifetime) {
        val content = getOrCreateContent(title, lt)
        getToolWindow(project).activate {}
        contentManager.setSelectedContent(content)
    }

    private fun getOrCreateTab(model: String, lt: Lifetime): SimpleToolWindowPanel {
        return getOrCreateContent(model, lt).component as SimpleToolWindowPanel
    }

    private fun getOrCreateContent(title: String, lt: Lifetime): Content {
        var content = contentMap[title]
        if (content == null || contentManager.getIndexOfContent(content) < 0) {
            val def = lt.createNested()

            content = createContent(def, project, title)
            contentMap[title] = content

            val listener = object : ContentManagerListener {
                override fun contentRemoved(event: ContentManagerEvent) {
                    if (event.content === content)
                        def.terminate()
                }
            }
            def.bracket({
                contentManager.addContentManagerListener(listener)
            }, {
                contentManager.removeContentManagerListener(listener)
            })
        }

        return content
    }

    private fun createContent(lt: Lifetime, project: Project, title: String): Content {
        val pane = UnrealLogPanel(title, lt, project)
        val content = contentManager.factory.createContent(pane, title, false)

        contentManager.addContent(content)

        return content
    }

    private fun getToolWindow(project: Project): ToolWindow {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        return toolWindowManager.getToolWindow(TOOLWINDOW_ID) ?: registerToolWindow(toolWindowManager, project)
    }
}
