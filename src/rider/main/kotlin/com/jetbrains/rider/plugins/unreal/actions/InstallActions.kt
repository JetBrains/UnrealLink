package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class InstallEditorPluginToEngineAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        project.solution.rdRiderModel.installEditorPlugin.fire(
                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.Yes, true, emptyList(), emptyList())
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val unrealHost = project.service<UnrealHost>()
        e.presentation.isEnabledAndVisible = unrealHost.isUnrealEngineSolution
    }
}

class InstallEditorPluginToGameAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        installOrExtractPluginInGame(project, ForceInstall.Yes, buildRequired = true)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val unrealHost = project.service<UnrealHost>()
        e.presentation.isEnabledAndVisible = unrealHost.isUnrealEngineSolution
    }
}

class ExtractEditorPluginToEngineAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        project.solution.rdRiderModel.installEditorPlugin.fire(
                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.Yes, false, emptyList(), emptyList())
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val unrealHost = project.service<UnrealHost>()
        e.presentation.isEnabledAndVisible = unrealHost.isUnrealEngineSolution && unrealHost.isPreBuiltEngine.not()
    }
}

class ExtractEditorPluginToGameAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        installOrExtractPluginInGame(project, ForceInstall.Yes, buildRequired = false)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val unrealHost = project.service<UnrealHost>()
        e.presentation.isEnabledAndVisible = unrealHost.isUnrealEngineSolution
    }
}

class DeleteRiderLinkPluginAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        project.solution.rdRiderModel.deletePlugin.fire()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val unrealHost = UnrealHost.getInstance(project)
        e.presentation.isEnabledAndVisible = unrealHost.isInstallInfoAvailable
    }
}

internal fun installOrExtractPluginInGame(project: Project, forceInstall: ForceInstall, buildRequired: Boolean) {
    val model = project.solution.rdRiderModel
    val unrealHost = project.service<UnrealHost>()
    val discoveredProjects = unrealHost.gamePluginInstallInfos
        .map { pluginInstallInfo ->
            UprojectItem(
                name = pluginInstallInfo.projectName,
                path = pluginInstallInfo.uprojectPath,
                installed = pluginInstallInfo.isPluginAvailable
            )
        }
        .sortedBy { it.name.lowercase() }

    if (discoveredProjects.size <= 1) {
        model.installEditorPlugin.fire(
            InstallPluginDescription(PluginInstallLocation.Game, forceInstall, buildRequired, discoveredProjects.map {it.path}, emptyList())
        )
        return
    }

    val confirmText = if (buildRequired) {
        UnrealLinkBundle.message("dialog.UnrealLink.InstallEditorPluginInGame.confirm.install")
    } else {
        UnrealLinkBundle.message("dialog.UnrealLink.InstallEditorPluginInGame.confirm.extract")
    }
    val dialog = InstallPluginInGameDialog(project, discoveredProjects, confirmText)
    if (!dialog.showAndGet()) return

    val selectedUprojectPaths = dialog.getSelectedPaths()
    val selected = selectedUprojectPaths.toHashSet()
    val unselectedUprojectPaths = discoveredProjects
        .asSequence()
        .map { it.path }
        .filterNot { selected.contains(it) }
        .toList()

    model.installEditorPlugin.fire(
        InstallPluginDescription(
            PluginInstallLocation.Game,
            forceInstall,
            buildRequired,
            selectedUprojectPaths,
            unselectedUprojectPaths
        )
    )
}

private data class UprojectItem(
    val name: String,
    val path: String,
    val installed: Boolean,
)

private class InstallPluginInGameDialog(
    project: Project,
    private val items: List<UprojectItem>,
    okText: String,
) : DialogWrapper(project, true) {
    private val checkboxes = mutableMapOf<String, JCheckBox>()

    init {
        title = UnrealLinkBundle.message("dialog.UnrealLink.InstallEditorPluginInGame.title")
        setOKButtonText(okText)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val content = JPanel(BorderLayout())
        val rowsPanel = JPanel(GridBagLayout())
        var row = 0

        rowsPanel.add(JLabel(""), gbc(0, row, 0.0))
        rowsPanel.add(JLabel(UnrealLinkBundle.message("dialog.UnrealLink.InstallEditorPluginInGame.column.project")), gbc(1, row, 0.0))
        rowsPanel.add(JLabel(UnrealLinkBundle.message("dialog.UnrealLink.InstallEditorPluginInGame.column.path")), gbc(2, row, 1.0))
        row++

        for (item in items) {
            val checkbox = JCheckBox().apply { isSelected = item.installed }
            checkboxes[item.path] = checkbox
            rowsPanel.add(checkbox, gbc(0, row, 0.0))
            rowsPanel.add(JLabel(item.name), gbc(1, row, 0.0))

            val pathField = JBTextField(item.path).apply {
                isEditable = false
                border = null
                isOpaque = false
            }
            rowsPanel.add(pathField, gbc(2, row, 1.0))
            row++
        }

        content.add(JBScrollPane(rowsPanel), BorderLayout.CENTER)
        return content
    }

    fun getSelectedPaths(): List<String> =
        items.asSequence()
            .filter { item -> checkboxes[item.path]?.isSelected == true }
            .map { it.path }
            .toList()

    private fun gbc(column: Int, row: Int, weightX: Double): GridBagConstraints = GridBagConstraints().apply {
        gridx = column
        gridy = row
        fill = GridBagConstraints.HORIZONTAL
        anchor = GridBagConstraints.WEST
        insets = JBUI.insets(2, 4)
        this.weightx = weightX
    }
}
