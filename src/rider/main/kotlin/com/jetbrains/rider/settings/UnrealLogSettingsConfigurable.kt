package com.jetbrains.rider.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.*
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.UnrealLinkSettings
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings

internal class UnrealLogSettingsConfigurable(private val project: Project) : DslConfigurableBase(), SearchableConfigurable {
    companion object {
        @NlsSafe
        const val UNREAL_LINK = "UnrealLink"
        const val ID = "UnrealLogSettings"
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = UNREAL_LINK

    override fun createPanel(): DialogPanel = panel {
        val commonSettings = project.service<UnrealLinkSettings>()
        val logSettings = project.service<UnrealLogPanelSettings>()

        group(UnrealLinkBundle.message("configurable.UnrealLink.toolbar.settings.label")) {
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.replaceWithHotReload.label"))
                        .bindSelected(commonSettings::replaceWithHotReload)
            }
        }

        group(UnrealLinkBundle.message("configurable.UnrealLink.log.settings.label")) {
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.clearLogOnStart.label"))
                        .bindSelected(logSettings::clearOnStart)
            }
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.focusLogOnStart.label"))
                        .bindSelected(logSettings::focusOnStart)
            }
            separator()
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.showTimestampsCheckbox.label"))
                        .bindSelected(logSettings::showTimestamps)
            }
            row {
                checkBox(UnrealLinkBundle.message("configurable.UnrealLink.showVerbosityCheckbox.label"))
                        .bindSelected(logSettings::showVerbosity)
            }
            row {
                val alignMessages =
                        checkBox(UnrealLinkBundle.message("configurable.UnrealLink.alignMessagesCheckbox.label"))
                                .bindSelected(logSettings::alignMessages)
                                .gap(RightGap.SMALL)
                intTextField(1..100, 1)
                        .columns(2)
                        .bindIntText(logSettings::categoryWidth)
                        .enabledIf(alignMessages.selected)
                        .gap(RightGap.SMALL)
                label(UnrealLinkBundle.message("configurable.UnrealLink.alignMessagesCheckbox.label.ending"))
            }
        }
    }
}