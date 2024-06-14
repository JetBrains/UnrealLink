package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rdclient.usages.SelfUpdatingCheckIcon
import kotlin.reflect.KMutableProperty0

class FilterCheckboxAction(
  text: String,
  private val isSelected: () -> Boolean,
  private val setSelected: (Boolean) -> Unit
) : DumbAwareAction(text) {
  constructor(text: String, property: KMutableProperty0<Boolean>) : this(text, property::get, property::set)

  private val checkboxIcon = SelfUpdatingCheckIcon(12) { isSelected() }

  init {
    templatePresentation.icon = checkboxIcon
    templatePresentation.selectedIcon = checkboxIcon
    templatePresentation.isMultiChoice = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val curState = isSelected()
    setSelected(!curState)
  }
}
