package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.openapi.rd.createNestedDisposable
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.plugins.unreal.model.LogMessageInfo
import com.jetbrains.rider.plugins.unreal.model.VerbosityType

class UnrealLogFilter(lifetime: Lifetime, private val settings: UnrealLogPanelSettings) {
    var showMessages: Boolean
        get() = settings.showMessages
        set(value) {
            settings.showMessages = value
        }
    var showWarnings: Boolean
        get() = settings.showWarnings
        set(value) {
            settings.showWarnings = value
        }
    var showErrors: Boolean
        get() = settings.showErrors
        set(value) {
            settings.showErrors = value
        }

    var showAllCategories: Boolean
        get() = settings.showAllCategories
        set(value) {
            if (settings.showAllCategories != value) {
                toggleAllCategories(value)
                settings.showAllCategories = value
            }
        }

    var showTimestamps: Boolean
        get() = settings.showTimestamps
        set(value) {
            settings.showTimestamps = value
        }

    var showVerbosity: Boolean
        get() = settings.showVerbosity
        set(value) {
            settings.showVerbosity = value
        }

    var alignMessages: Boolean
        get() = settings.alignMessages
        set(value) {
            settings.alignMessages = value
        }

    var categoryWidth: Int
        get() = settings.categoryWidth
        set(value) {
            settings.categoryWidth = value
        }

    private val categories: HashSet<String> = hashSetOf()
    private val selectedCategories: HashSet<String> = hashSetOf()

    private val filterChangedListeners: ArrayList<() -> Unit> = arrayListOf()
    private val onCategoryAddedListeners: ArrayList<(String) -> Unit> = arrayListOf()

    init {
        val disposable = lifetime.createNestedDisposable()
        settings.addSettingsChangedListener({ onFilterChanged() }, disposable)
    }

    fun addFilterChangedListener(listener: () -> Unit) {
        filterChangedListeners.add(listener)
    }

    private fun onFilterChanged() {
        filterChangedListeners.forEach { it.invoke() }
    }

    fun isMessageAllowed(message: LogMessageInfo): Boolean {
        // see FOutputLogFilter::IsMessageAllowed from SOutputLog.cpp

        // Checking verbosity
        val verbosity = message.type
        if (verbosity == VerbosityType.Error && !showErrors) {
            return false
        }

        if (verbosity == VerbosityType.Warning && !showWarnings) {
            return false
        }

        if (verbosity != VerbosityType.Error && verbosity != VerbosityType.Warning && !showMessages) {
            return false
        }

        // Checking if category is selected
        val category = message.category.data
        if (category !in selectedCategories) {
            return false
        }

        return true
    }

    fun addCategory(category: String) {
        if (!categories.add(category)) {
            return
        }

        // new categories selected state relies on showAllCategories state
        val isSelected = showAllCategories
        if (isSelected) {
            selectedCategories.add(category)
        }

        fireOnCategoryAdded(category)
    }

    fun addOnCategoryAddedListener(listener: (String) -> Unit) {
        onCategoryAddedListeners.add(listener)
    }

    private fun fireOnCategoryAdded(category: String) {
        onCategoryAddedListeners.forEach { it.invoke(category) }
    }

    fun isCategorySelected(category: String): Boolean {
        return category in selectedCategories
    }

    fun toggleCategory(category: String, state: Boolean) {
        if (state) {
            selectedCategories.add(category)
        } else {
            selectedCategories.remove(category)
        }

        onFilterChanged()
    }

    private fun toggleAllCategories(state: Boolean) {
        selectedCategories.clear()
        if (state) {
            selectedCategories.addAll(categories)
        }
    }
}