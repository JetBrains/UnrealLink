package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "UnrealLinkSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class UnrealLinkSettings : SimplePersistentStateComponent<UnrealLinkSettings.State>(State()) {
    companion object {
        fun getInstance(project: Project): UnrealLinkSettings = project.service()
    }

    var replaceWithHotReload: Boolean
        get() = state.replaceWithHotReload
        set(value) {
            state.replaceWithHotReload = value
        }

    class State : BaseState() {
        var replaceWithHotReload by property(defaultValue = true)
    }
}
