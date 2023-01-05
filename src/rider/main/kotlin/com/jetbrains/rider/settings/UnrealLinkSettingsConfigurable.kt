package com.jetbrains.rider.settings

import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.settings.simple.SimpleOptionsPage

class UnrealLinkSettingsConfigurable : SimpleOptionsPage(UnrealLinkBundle.message("configurable.UnrealLink.settings.title"), "UnrealLinkOptions") {
    override fun getId() = pageId + "Id"
}
