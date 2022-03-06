package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.openapi.rd.createNestedDisposable
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.plugins.unreal.model.LogMessageInfo
import com.jetbrains.rider.plugins.unreal.model.VerbosityType

class UnrealLogFilter(lifetime: Lifetime, private val settings: UnrealLogPanelSettings) {
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
        if (verbosity == VerbosityType.Error && !settings.showErrors) {
            return false
        }

        if (verbosity == VerbosityType.Warning && !settings.showWarnings) {
            return false
        }

        if (verbosity != VerbosityType.Error && verbosity != VerbosityType.Warning && !settings.showMessages) {
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
        val isSelected = settings.showAllCategories
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

    fun toggleAllCategories(state: Boolean) {
        selectedCategories.clear()
        if (state) {
            selectedCategories.addAll(categories)
        }
    }
}