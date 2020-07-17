package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl

import javax.swing.*

class FilterAction constructor(text: String, parent: FilterComboAction) : AnAction(text), DumbAware {
    val myParent: FilterComboAction = parent
    override fun update(e: AnActionEvent) {
        e.getPresentation().setEnabledAndVisible(true)
    }

    override fun actionPerformed(e: AnActionEvent) {
        myParent.setText(e.getPresentation().getText())
    }
}

class FilterComboAction constructor(defaultItem: String, initialItems: ArrayList<String>): ComboBoxAction(), DumbAware {
    private var myText: String = defaultItem
    private var myItems: ArrayList<String> = initialItems
    private var myItemListeners: ArrayList<(String) -> Unit> = arrayListOf()

    override fun update(e: AnActionEvent) {
        val presentation: Presentation = e.getPresentation()
        presentation.setText(myText)
        presentation.setEnabledAndVisible(true)
    }

    override protected fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        val group = DefaultActionGroup()
        for (item in myItems) {
            group.add(FilterAction(item, this))
        }
        return group
    }

    fun setText(text: String) {
        if (text != myText) {
            myText = text
            for (listener in myItemListeners) {
                listener.invoke(myText)
            }
        }
    }

    fun addItem(text: String) {
        myItems.add(text)
    }

    fun items() : List<String> {
        return myItems
    }

    fun addItemListener(listener : (String) -> Unit) {
        myItemListeners.add(listener)
    }
}