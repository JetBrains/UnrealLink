package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.status.StatusBarUtil
import com.intellij.ui.content.Content
import com.jetbrains.rd.platform.util.idea.LifetimedProjectService
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rider.build.BuildToolWindowFactory
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ContentType
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallMessage
import java.awt.CardLayout
import javax.swing.JPanel

@Service
class RiderLinkInstallService(
    project: Project
) : LifetimedProjectService(project) {

    companion object {
        fun getInstance(project: Project): RiderLinkInstallService = project.service()
    }

    private var context: RiderLinkInstallContext? = null
    private val buildToolWindowFactory = ServiceManager.getService(project, BuildToolWindowFactory::class.java)


    fun getOrCreateRiderLinkInstallContext(): RiderLinkInstallContext {
        val currentContext = context
        if (currentContext != null)
            return currentContext
        val toolWindow = buildToolWindowFactory.getOrRegisterToolWindow()
        val contentManager = toolWindow.contentManager

        val panel = RiderLinkInstallPanel(project, projectServiceLifetime)
        val toolWindowContent = contentManager.factory.createContent(null, "RiderLink Install Progress", true).apply {
            StatusBarUtil.setStatusBarInfo(project, "Install")
            component = panel
            isCloseable = false
            icon = AllIcons.Actions.Install
        }
        contentManager.addContent(toolWindowContent)
        val ctx = RiderLinkInstallContext(toolWindow, toolWindowContent, panel)
        context = ctx
        return ctx
    }
}

fun ConsoleViewImpl.println(text: String, type: ConsoleViewContentType) {
    print(text, type)
    print("\n", type)
}


class RiderLinkInstallPanel(
    project: Project,
    lifetime: Lifetime
) : SimpleToolWindowPanel(false) {
    private val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console as ConsoleViewImpl
    private val layout = CardLayout()
    private val container: JPanel

    init {
        Disposer.register(project, console)
        setProvideQuickActions(true)
        container = JPanel(layout).apply {
            add(console.component, "console")
        }
        setContent(container)
        lifetime.onTermination {
            console.dispose()
        }
    }

    fun writeMessage(message: InstallMessage) {
        when (message.type) {
            ContentType.Normal -> console.println(message.text, ConsoleViewContentType.NORMAL_OUTPUT)
            ContentType.Error -> console.println(message.text, ConsoleViewContentType.ERROR_OUTPUT)
        }
    }

    fun clear() {
        console.clear()
    }
}

class RiderLinkInstallContext(
    private val toolWindow: ToolWindow,
    private val content: Content,
    private val panel: RiderLinkInstallPanel
) {

    private fun makeActive() {
        toolWindow.contentManager.setSelectedContent(content)
    }

    fun showToolWindowIfHidden() {
        if (!toolWindow.isActive)
            toolWindow.activate {}
        makeActive()
    }

    fun writeMessage(message: InstallMessage) {
        panel.writeMessage(message)
    }

    fun clear() {
        panel.clear()
    }

    var isActive = false
}