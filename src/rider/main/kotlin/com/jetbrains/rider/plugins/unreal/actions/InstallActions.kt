package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rider.plugins.unreal.UnrealHostSetup
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
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
        val unrealHostSetup = project.getService(UnrealHostSetup::class.java)
        e.presentation.isEnabledAndVisible = unrealHostSetup.isUnrealEngineSolution
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
        val unrealHostSetup = project.getService(UnrealHostSetup::class.java)
        e.presentation.isEnabledAndVisible = unrealHostSetup.isUnrealEngineSolution
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
        val unrealHostSetup = project.getService(UnrealHostSetup::class.java)
        e.presentation.isEnabledAndVisible = unrealHostSetup.isUnrealEngineSolution && unrealHostSetup.isPreBuiltEngine.not()
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
        val unrealHostSetup = project.getService(UnrealHostSetup::class.java)
        e.presentation.isEnabledAndVisible = unrealHostSetup.isUnrealEngineSolution
    }
}