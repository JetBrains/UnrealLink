package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import java.util.*


@State(name = "UnrealLogPanelSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class UnrealLogPanelSettings(private val project: Project) : SimplePersistentStateComponent<UnrealLogPanelSettings.State>(State()) {
    companion object {
        fun getInstance(project: Project): UnrealLogPanelSettings = project.service()
    }

    var showMessages: Boolean
        get() = state.showMessages
        set(value) {
            if (state.showMessages != value) {
                state.showMessages = value
                fireSettingsChanged()
            }
        }
    var showWarnings: Boolean
        get() = state.showWarnings
        set(value) {
            if (state.showWarnings != value) {
                state.showWarnings = value
                fireSettingsChanged()
            }
        }
    var showErrors: Boolean
        get() = state.showErrors
        set(value) {
            if (state.showErrors != value) {
                state.showErrors = value
                fireSettingsChanged()
            }
        }
    var showAllCategories: Boolean
        get() = state.showAllCategories
        set(value) {
            if (state.showAllCategories != value) {
                state.showAllCategories = value
                fireSettingsChanged()
            }
        }

    var showTimestamps: Boolean
        get() = state.showTimestamps
        set(value) {
            if (state.showTimestamps != value) {
                state.showTimestamps = value
                fireSettingsChanged()
            }
        }
    var showVerbosity: Boolean
        get() = state.showVerbosity
        set(value) {
            if (state.showVerbosity != value) {
                state.showVerbosity = value
                fireSettingsChanged()
            }
        }
    var alignMessages: Boolean
        get() = state.alignMessages
        set(value) {
            if (state.alignMessages != value) {
                state.alignMessages = value
                fireSettingsChanged()
            }
        }
    var categoryWidth: Int
        get() = state.categoryWidth
        set(value) {
            if (state.categoryWidth != value) {
                state.categoryWidth = value
                fireSettingsChanged()
            }
        }

    fun interface SettingsChangedListener : EventListener {
        fun settingsChanged()
    }

    private val settingsChangedDispatcher: EventDispatcher<SettingsChangedListener> =
            EventDispatcher.create(SettingsChangedListener::class.java)

    fun addSettingsChangedListener(listener: SettingsChangedListener, disposable: Disposable) {
        settingsChangedDispatcher.addListener(listener, disposable)
    }

    private fun fireSettingsChanged() {
        settingsChangedDispatcher.multicaster.settingsChanged()
    }

    class State : BaseState() {
        var showMessages by property(defaultValue = true)
        var showWarnings by property(defaultValue = true)
        var showErrors by property(defaultValue = true)
        var showAllCategories by property(defaultValue = true)

        var showTimestamps by property(defaultValue = false)
        var showVerbosity by property(defaultValue = true)
        var alignMessages by property(defaultValue = true)
        var categoryWidth by property(defaultValue = 25)
    }
}

