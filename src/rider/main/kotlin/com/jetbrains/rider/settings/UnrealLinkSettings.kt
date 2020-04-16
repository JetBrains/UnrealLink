package com.jetbrains.rider.settings

import com.intellij.openapi.options.ConfigurableProvider
import com.jetbrains.rider.settings.simple.SimpleOptionsPage

class UnrealLinkSettings : SimpleOptionsPage("Unreal Engine", "UnrealLinkOptions") {
    override fun getId() = pageId + "Id"
}

class UnrealLinkSettingsConfigurableProvider : ConfigurableProvider() {
    override fun createConfigurable() = UnrealLinkSettings()
    override fun canCreateConfigurable(): Boolean = true
}
