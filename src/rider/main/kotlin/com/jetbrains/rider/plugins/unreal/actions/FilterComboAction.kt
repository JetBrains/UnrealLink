package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl

import javax.swing.*

class FilterCheckboxAction constructor(text: String, defaultSelected: Boolean) : CheckboxAction(text), DumbAware {
    private var myIsSelected: Boolean = defaultSelected
    private val myName: String = text

    override fun isSelected(e: AnActionEvent): Boolean {
        return myIsSelected
    }

    fun isSelected(): Boolean {
        return myIsSelected
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        myIsSelected = state
    }

    fun setSelected(state: Boolean) {
        myIsSelected = state
    }


    fun getName(): String {
        return myName
    }
}

class FilterComboAction constructor(defaultItem: String): ComboBoxAction(), DumbAware {
    private var myText: String = defaultItem
    private var myItems: ArrayList<FilterCheckboxAction> = arrayListOf()
    private var myItemListeners: ArrayList<() -> Unit> = arrayListOf()

    override fun update(e: AnActionEvent) {
        val presentation: Presentation = e.getPresentation()
        presentation.setText(myText)
        presentation.setEnabledAndVisible(true)
        for (listener in myItemListeners) {
            listener.invoke()
        }
    }

    override protected fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        val group = DefaultActionGroup()
        for (item in myItems) {
            group.add(item)
        }
        return group
    }

    fun addItem(item: FilterCheckboxAction) {
        myItems.add(item)
    }

    fun items() : List<FilterCheckboxAction> {
        return myItems
    }

    fun selected() : List<String> {
        var result = arrayListOf<String>()
        for (item in myItems) {
            if (item.isSelected()) {
                result.add(item.getName())
            }
        }
        return result
    }

    fun selectAll(select: Boolean) {
        for (item in myItems) {
            item.setSelected(select)
        }
    }

    fun addItemListener(listener : () -> Unit) {
        myItemListeners.add(listener)
    }
}