package com.jetbrains.rider.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.*
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings

class UnrealLogSettingsConfigurable(private val project: Project) : DslConfigurableBase(), SearchableConfigurable {
    companion object {
        @NlsSafe
        internal const val UNREAL_LINK = "UnrealLink"
    }

    override fun getId(): String = "UnrealLogSettings"

    override fun getDisplayName(): String = UNREAL_LINK

    override fun createPanel(): DialogPanel = panel {
        val settings = project.service<UnrealLogPanelSettings>()

        group(UnrealLinkBundle.message("configurable.UnrealLink.log.settings.label")) {
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.clearLogOnStart.label"))
                        .bindSelected(settings::clearOnStart)
            }
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.focusLogOnStart.label"))
                        .bindSelected(settings::focusOnStart)
            }
            separator()
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.showTimestampsCheckbox.label"))
                        .bindSelected(settings::showTimestamps)
            }
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.showVerbosityCheckbox.label"))
                        .bindSelected(settings::showVerbosity)
            }
            row {
                val alignMessages =
                        checkBox(UnrealLinkBundle.message("configurable.UnrealLink.alignMessagesCheckbox.label"))
                                .bindSelected(settings::alignMessages)
                                .gap(RightGap.SMALL)
                intTextField(1..100, 1)
                        .columns(2)
                        .bindIntText(settings::categoryWidth)
                        .enabledIf(alignMessages.selected)
                        .gap(RightGap.SMALL)
                label(UnrealLinkBundle.message("configurable.UnrealLink.alignMessagesCheckbox.label.ending"))
            }
        }
    }
}