package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import javax.swing.JComponent

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

    private var myLastSelected: ArrayList<String> = arrayListOf()

    override fun update(e: AnActionEvent) {
        val presentation: Presentation = e.presentation
        presentation.text = myText
        presentation.isEnabledAndVisible = true
        val currentSelected = selected()
        if (myLastSelected != currentSelected) {
            myLastSelected = currentSelected
            for (listener in myItemListeners) {
                listener.invoke()
            }
        }

    }

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        val group = DefaultActionGroup()
        for (item in myItems) {
            group.add(item)
        }
        return group
    }

    fun addItem(item: FilterCheckboxAction) {
        myItems.add(item)
        if (item.isSelected()) {
            myLastSelected.add(item.getName())
        }
    }

    fun items() : ArrayList<FilterCheckboxAction> {
        return myItems
    }

    fun selected() : ArrayList<String> {
        val result = arrayListOf<String>()
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