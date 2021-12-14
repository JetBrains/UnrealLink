package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(name = "UnrealLogPanelSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class UnrealLogPanelSettings(private val project: Project) : SimplePersistentStateComponent<UnrealLogPanelSettings.State>(State()) {
    companion object {
        fun getInstance(project: Project): UnrealLogPanelSettings = project.service()
    }

    var showTimestamps: Boolean
        get() = state.showTimestamps
        set(value) {
            state.showTimestamps = value
        }
    var showMessages: Boolean
        get() = state.showMessages
        set(value) {
            state.showMessages = value
        }
    var showWarnings: Boolean
        get() = state.showWarnings
        set(value) {
            state.showWarnings = value
        }
    var showErrors: Boolean
        get() = state.showErrors
        set(value) {
            state.showErrors = value
        }
    var showAllCategories: Boolean
        get() = state.showAllCategories
        set(value) {
            state.showAllCategories = value
        }

    class State : BaseState() {
        var showTimestamps by property(defaultValue = false)

        var showMessages by property(defaultValue = true)
        var showWarnings by property(defaultValue = true)
        var showErrors by property(defaultValue = true)
        var showAllCategories by property(defaultValue = true)
    }
}

