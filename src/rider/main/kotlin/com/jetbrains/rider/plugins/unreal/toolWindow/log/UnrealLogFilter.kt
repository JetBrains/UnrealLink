package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.jetbrains.rider.plugins.unreal.model.LogMessageInfo
import com.jetbrains.rider.plugins.unreal.model.VerbosityType

class UnrealLogFilter {
    var showMessages: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                onFilterChanged()
            }
        }
    var showWarnings: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                onFilterChanged()
            }
        }
    var showErrors: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                onFilterChanged()
            }
        }

    var showAllCategories: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                toggleAllCategories(field)
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