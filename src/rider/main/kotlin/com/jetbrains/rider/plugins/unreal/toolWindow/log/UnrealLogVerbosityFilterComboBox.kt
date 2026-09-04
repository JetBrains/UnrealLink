package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.actions.FilterCheckboxAction
import javax.swing.JComponent

class UnrealLogVerbosityFilterComboBox(settings: UnrealLogPanelSettings) : ComboBoxAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    init {
        val presentation = this.templatePresentation
        presentation.text = UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbositySelection.label")
    }

    private val fatalCheckBox = FilterCheckboxAction(
        UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Fatal.text"), settings::showFatal)
    private val displayCheckBox = FilterCheckboxAction(
        UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Display.text"), settings::showDisplay)
    private val logCheckBox = FilterCheckboxAction(
        UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Log.text"), settings::showLog)
    private val verboseCheckBox = FilterCheckboxAction(
        UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Verbose.text"), settings::showVerbose)
    private val veryVerboseCheckBox = FilterCheckboxAction(
        UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.VeryVerbose.text"), settings::showVeryVerbose)
    private val warningsCheckBox: FilterCheckboxAction =
            FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Warnings.text"), settings::showWarnings)
    private val errorsCheckBox: FilterCheckboxAction =
            FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.verbosity.Errors.text"), settings::showErrors)
    private val popupGroup: DefaultActionGroup = VerbosityActionGroup()

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        return popupGroup
    }

    inner class VerbosityActionGroup : DefaultActionGroup() {
        init {
            add(fatalCheckBox)
            add(errorsCheckBox)
            add(warningsCheckBox)
            add(displayCheckBox)
            add(logCheckBox)
            add(verboseCheckBox)
            add(veryVerboseCheckBox)
        }
    }

}
