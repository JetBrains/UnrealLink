package com.jetbrains.rider.plugins.unreal

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.status.StatusBarUtil
import com.intellij.ui.content.Content
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.build.BuildToolWindowFactory
import com.jetbrains.rider.build.BuildToolWindowService
import com.jetbrains.rider.plugins.unreal.actions.CancelRiderLinkInstallAction
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ContentType
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallMessage
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
class RiderLinkInstallService(
    val project: Project
) : LifetimedService() {
    companion object {
        fun getInstance(project: Project): RiderLinkInstallService = project.service()
        val logger = getLogger<RiderLinkInstallService>()
    }

    private var context: RiderLinkInstallContext? = null
    private val buildToolWindowFactory = project.getService(BuildToolWindowService::class.java)


    fun getOrCreateRiderLinkInstallContext(): RiderLinkInstallContext {
        logger.info { "[UnrealLink]: ${::getOrCreateRiderLinkInstallContext.name}" }
        val currentContext = context
        if (currentContext != null)
            return currentContext
        val toolWindow = buildToolWindowFactory.getOrRegisterToolWindow(project)
        val contentManager = toolWindow.contentManager

        val panel = RiderLinkInstallPanel(project, this, serviceLifetime)
        val toolWindowContent = contentManager.factory.createContent(null, UnrealLinkBundle.message("RiderLink.InstallProgress.text.title"), true).apply {
            StatusBarUtil.setStatusBarInfo(project, "Install")
            component = panel
            panel.toolbar = createToolbarPanel(panel)
            isCloseable = false
            icon = AllIcons.Actions.Install
        }
        contentManager.addContent(toolWindowContent)
        val ctx = RiderLinkInstallContext(toolWindow, toolWindowContent, panel)
        context = ctx
        return ctx
    }

    private fun createToolbarPanel(mainPanel: RiderLinkInstallPanel): JPanel {
        logger.info { "[UnrealLink]: ${::createToolbarPanel.name}" }
        val buildActionGroup = DefaultActionGroup().apply {
            add(CancelRiderLinkInstallAction())
        }
        val panel = JPanel(BorderLayout())
        val toolbar = ActionManager.getInstance().createActionToolbar("UnrealLink.BuildRiderLinkToolbar", buildActionGroup, false)
        toolbar.targetComponent = mainPanel
        panel.add(toolbar.component, BorderLayout.WEST)

        return panel
    }
}

fun ConsoleViewImpl.println(text: String, type: ConsoleViewContentType) {
    print(text, type)
    print("\n", type)
}


class RiderLinkInstallPanel(
    project: Project,
    riderLinkInstallService: RiderLinkInstallService,
    lifetime: Lifetime
) : SimpleToolWindowPanel(false) {
    companion object {
        val logger = getLogger<RiderLinkInstallPanel>()
    }
    private val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console as ConsoleViewImpl
    private val container: JPanel

    init {
        logger.info { "[UnrealLink]: init" }

        Disposer.register(riderLinkInstallService, console)
        setProvideQuickActions(true)
        container = JPanel(CardLayout()).apply {
            add(console.component, "console")
        }
        setContent(container)
        lifetime.onTermination {
            console.dispose()
        }
    }

    fun writeMessage(message: InstallMessage) {
        logger.info { "[UnrealLink]: ${::writeMessage.name} $message" }
        when (message.type) {
            ContentType.Normal -> console.println(message.text, ConsoleViewContentType.NORMAL_OUTPUT)
            ContentType.Error -> console.println(message.text, ConsoleViewContentType.ERROR_OUTPUT)
        }
    }

    fun clear() {
        logger.info { "[UnrealLink]: ${::clear.name}" }
        console.clear()
    }
}

class RiderLinkInstallContext(
    private val toolWindow: ToolWindow,
    private val content: Content,
    private val panel: RiderLinkInstallPanel
) {
    companion object {
        val logger = getLogger<RiderLinkInstallContext>()
    }
    private fun makeActive() {
        logger.info { "[UnrealLink]: ${::makeActive.name}" }
        toolWindow.contentManager.setSelectedContent(content)
    }

    fun showToolWindowIfHidden() {
        logger.info { "[UnrealLink]: ${::showToolWindowIfHidden.name} toolWindow.isActive ${toolWindow.isActive}" }
        if (!toolWindow.isActive)
            toolWindow.activate {}
        makeActive()
    }

    fun writeMessage(message: InstallMessage) {
        logger.info { "[UnrealLink]: ${::writeMessage.name} $message" }
        panel.writeMessage(message)
    }

    fun clear() {
        logger.info { "[UnrealLink]: ${::clear.name}" }
        panel.clear()
    }
}