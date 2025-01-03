package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.actions.FilterCheckboxAction
import javax.swing.JComponent

class UnrealLogCategoryFilterComboBox(private val settings: UnrealLogPanelSettings,
                                      private val logFilter: UnrealLogFilter) : ComboBoxAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    init {
        val presentation = this.templatePresentation
        presentation.text = UnrealLinkBundle.message("toolWindow.UnrealLog.settings.categoriesSelection.label")

        logFilter.addOnCategoryAddedListener(this::onCategoryAdded)
    }

    private var showAllCategories: Boolean
        get() = settings.showAllCategories
        set(value) {
            if (settings.showAllCategories != value) {
                logFilter.toggleAllCategories(value)
                settings.showAllCategories = value
            }
        }


    private val allSelectedCheckbox: FilterCheckboxAction =
            FilterCheckboxAction(UnrealLinkBundle.message("toolWindow.UnrealLog.settings.categories.ShowAll.text"), ::showAllCategories)

    // sorted list of categories
    private val categoriesCheckboxes: ArrayList<FilterCheckboxAction> = arrayListOf()

    override fun createPopupActionGroup(parent: JComponent?): DefaultActionGroup {
        val group = DefaultActionGroup()
        group.add(allSelectedCheckbox)
        group.addSeparator()
        group.addAll(categoriesCheckboxes)
        return group
    }

    private fun isCategorySelected(category: String): Boolean = logFilter.isCategorySelected(category)

    private fun selectCategory(category: String, state: Boolean) = logFilter.toggleCategory(category, state)

    private fun onCategoryAdded(category: String) {
        var idx = categoriesCheckboxes.binarySearchBy(category) { it.templatePresentation.text }
        if (idx >= 0) return

        val checkbox = FilterCheckboxAction(category,
                { isCategorySelected(category) },
                { selectCategory(category, it) }
        )

        // index to insert
        idx = -idx - 1
        categoriesCheckboxes.add(idx, checkbox)
    }
}
