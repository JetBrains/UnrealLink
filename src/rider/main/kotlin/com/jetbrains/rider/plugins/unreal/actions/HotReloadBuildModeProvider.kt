package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.project.Project
import com.jetbrains.rider.build.actions.BuildButtonModeProvider
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.UnrealLinkSettings

class HotReloadBuildModeProvider : BuildButtonModeProvider {
    override fun getPriority(): Int {
        return 10000
    }

    override fun isApplicable(project: Project): Boolean {
        val host = UnrealHost.getInstance(project)
        val settings: UnrealLinkSettings = UnrealLinkSettings.getInstance(project)
        return host.isUnrealEngineSolution && host.isHotReloadAvailable && settings.replaceWithHotReload
    }

    override fun getButtonActionId(): String {
        return "UnrealLink.HotReloadBuild"
    }
}