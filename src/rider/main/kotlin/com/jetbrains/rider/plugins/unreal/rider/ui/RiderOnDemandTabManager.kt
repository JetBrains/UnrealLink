package com.jetbrains.rider.plugins.unreal.rider.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.jetbrains.rd.util.lifetime.Lifetime

class RiderOnDemandTabManager<in T : Any?> internal constructor(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val titleFactory : (T) -> String,
    private val tabContentFactory: (T, Lifetime,  Project) -> SimpleToolWindowPanel,
    private val keyFunction: (T) -> Any?) {

    private val contentMap: HashMap<Any?, Content> = HashMap()
    private val contentManager = toolWindow.contentManager

    fun showTab(model: T, lt: Lifetime) {
        var content = contentMap[keyFunction(model)]
        if (content == null || contentManager.getIndexOfContent(content) < 0) {

            content = createContent(lt, project, model)

            contentMap[keyFunction(model)] = content
        }

        toolWindow.activate {}
        contentManager.setSelectedContent(content)
    }

    private fun createContent(lt: Lifetime, project: Project, model: T): Content {
        val pane = tabContentFactory(model, lt, project)
        val content = contentManager.factory.createContent(pane, titleFactory(model), false)

        contentManager.addContent(content)

        return content
    }
}