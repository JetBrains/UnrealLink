package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.*
import com.jetbrains.rider.projectView.solution

class InstallEditorPluginToEngineAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        project.solution.rdRiderModel.installEditorPlugin.fire(
                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.Yes)
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
        project.solution.rdRiderModel.installEditorPlugin.fire(
                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes)
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

class ExtractEditorPluginToEngineAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        project.solution.rdRiderModel.installEditorPlugin.fire(
                InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.Yes, false)
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
        project.solution.rdRiderModel.installEditorPlugin.fire(
                InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes, false)
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