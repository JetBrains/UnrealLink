package com.jetbrains.rider.settings

import com.jetbrains.rider.settings.simple.SimpleOptionsPage

class UnrealLinkSettingsConfigurable : SimpleOptionsPage("Unreal Engine", "UnrealLinkOptions") {
    override fun getId() = pageId + "Id"
}
