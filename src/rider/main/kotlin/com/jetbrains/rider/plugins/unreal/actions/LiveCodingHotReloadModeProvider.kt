package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.project.Project
import com.jetbrains.rider.build.actions.BuildButtonModeProvider
import com.jetbrains.rider.plugins.unreal.UnrealHost

class LiveCodingHotReloadModeProvider : BuildButtonModeProvider {
    override fun getPriority(): Int {
        return 10000
    }

    override fun isApplicable(project: Project): Boolean {
        val host = UnrealHost.getInstance(project)
        return host.isUnrealEngineSolution && host.isLiveCodingAvailable
        // TODO: Should also check that configuration is "correct" (built&run Unreal project)?
    }

    override fun getButtonActionId(): String {
        return "UnrealLink.LiveCodingHotReload"
    }
}