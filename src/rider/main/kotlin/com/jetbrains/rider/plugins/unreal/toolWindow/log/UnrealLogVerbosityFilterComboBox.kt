package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.actions.FilterCheckboxAction
import javax.swing.JComponent

class UnrealLogVerbosityFilterComboBox(settings: UnrealLogPanelSettings) : ComboBoxAction(), DumbAware {
    init {
        val presentation = this.templatePresentation
        presentation.text = UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbositySelection.label")
    }

    private val messagesCheckBox: FilterCheckboxAction =
            FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Messages.text"), settings::showMessages)
    private val warningsCheckBox: FilterCheckboxAction =
            FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Warnings.text"), settings::showWarnings)
    private val errorsCheckBox: FilterCheckboxAction =
            FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Errors.text"), settings::showErrors)
    private val popupGroup: DefaultActionGroup = VerbosityActionGroup()

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
        super.update(e)
    }

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        return popupGroup
    }

    inner class VerbosityActionGroup : DefaultActionGroup() {
        init {
            add(messagesCheckBox)
            add(warningsCheckBox)
            add(errorsCheckBox)
        }
    }

}
