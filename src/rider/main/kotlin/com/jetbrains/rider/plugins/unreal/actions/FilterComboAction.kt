package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.project.DumbAware
import kotlin.reflect.KMutableProperty0

class FilterCheckboxAction(
        text: String,
        private val isSelected: () -> Boolean,
        private val setSelected: (Boolean) -> Unit
) : CheckboxAction(text), DumbAware {
    constructor(text: String, property: KMutableProperty0<Boolean>) : this(text, property::get, property::set)

    override fun isSelected(e: AnActionEvent): Boolean {
        return isSelected()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        setSelected(state)
    }
}
