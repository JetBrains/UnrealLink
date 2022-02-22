package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.actions.FilterCheckboxAction


class UnrealLogSettingsActionGroup(private val settings: UnrealLogFilter) : DefaultActionGroup(), DumbAware {
    init {
        isPopup = true
        templatePresentation.icon = AllIcons.General.GearPlain

        add(FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.clearLogOnStart.label"), settings::clearOnStart))
        add(FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.focusLogOnStart.label"), settings::focusOnStart))
        addSeparator()
        add(FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.showTimestampsCheckbox.label"), settings::showTimestamps))
        add(FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.showVerbosityCheckbox.label"), settings::showVerbosity))
        add(FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.alignMessagesCheckbox.label"), settings::alignMessages))
    }
}
