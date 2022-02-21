package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.jetbrains.rider.plugins.unreal.model.LogMessageInfo
import com.jetbrains.rider.plugins.unreal.model.VerbosityType

class UnrealLogFilter(private val settings: UnrealLogPanelSettings) {
    var showMessages: Boolean
        get() = settings.showMessages
        set(value) {
            if (settings.showMessages != value) {
                settings.showMessages = value
                onFilterChanged()
            }
        }
    var showWarnings: Boolean
        get() = settings.showWarnings
        set(value) {
            if (settings.showWarnings != value) {
                settings.showWarnings = value
                onFilterChanged()
            }
        }
    var showErrors: Boolean
        get() = settings.showErrors
        set(value) {
            if (settings.showErrors != value) {
                settings.showErrors = value
                onFilterChanged()
            }
        }

    var showAllCategories: Boolean
        get() = settings.showAllCategories
        set(value) {
            if (settings.showAllCategories != value) {
                settings.showAllCategories = value
                toggleAllCategories(value)
            }
        }

    var showTimestamps: Boolean
        get() = settings.showTimestamps
        set(value) {
            if (settings.showTimestamps != value) {
                settings.showTimestamps = value
                onFilterChanged()
            }
        }

    var showVerbosity: Boolean
        get() = settings.showVerbosity
        set(value) {
            if (settings.showVerbosity != value) {
                settings.showVerbosity = value
                onFilterChanged()
            }
        }

    var alignMessages: Boolean
        get() = settings.alignMessages
        set(value) {
            if (settings.alignMessages != value) {
                settings.alignMessages = value
                onFilterChanged()
            }
        }

    var categoryWidth: Int
        get() = settings.categoryWidth
        set(value) {
            if (settings.categoryWidth != value) {
                settings.categoryWidth = value
                onFilterChanged()
            }
        }

    private val categories: HashSet<String> = hashSetOf()
    private val selectedCategories: HashSet<String> = hashSetOf()

    private val filterChangedListeners: ArrayList<() -> Unit> = arrayListOf()
    private val onCategoryAddedListeners: ArrayList<(String) -> Unit> = arrayListOf()

    fun addFilterChangedListener(listener: ()-> Unit) {
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

    fun addOnCategoryAddedListener(listener: (String)-> Unit) {
        onCategoryAddedListeners.add(listener)
    }

    private fun fireOnCategoryAdded(category: String) {
        onCategoryAddedListeners.forEach { it.invoke(category) }
    }

    fun isCategorySelected(category: String) : Boolean {
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

        onFilterChanged()
    }

}