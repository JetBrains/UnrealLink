package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import icons.RiderIcons
import javax.swing.JComponent

class CancelRiderLinkInstallAction : AnAction(), CustomComponentAction, DumbAware {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return ActionButton(this, presentation, place, DEFAULT_MINIMUM_BUTTON_SIZE)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        e.presentation.icon = RiderIcons.Actions.CancelCompile
        e.presentation.isEnabled = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?:  error("UnrealHost not found")
        host.model.cancelRiderLinkInstall.fire(Unit)
    }
}