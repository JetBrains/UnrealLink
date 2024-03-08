package com.jetbrains.rider.plugins.unreal.debugger.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.BadgeIconSupplier
import com.jetbrains.rider.plugins.unreal.actions.UnrealHostOperations

class FramesPopupGroup : DefaultActionGroup(), DumbAware {
  private val FILTER_ICON = BadgeIconSupplier(AllIcons.General.Filter)
  private val myFilterActiveIcon = FILTER_ICON.getLiveIndicatorIcon(true)
  private val myFilterInactiveIcon = FILTER_ICON.getLiveIndicatorIcon(false)

  override fun update(e: AnActionEvent) {
    super.update(e)

    val icon = if (isAllVisibleChildrenSelected(e)) myFilterInactiveIcon else myFilterActiveIcon

    UnrealHostOperations.updatePresentationBasedOnUnrealAvailability(e, icon, icon)
  }

  private fun isAllVisibleChildrenSelected(e: AnActionEvent): Boolean {
    getChildren(e).forEach {
      when (it) {
        is UnrealToggleBaseAction -> {
          if (!(!it.isVisible || it.isSelected(e))) return false
        }
        is ToggleAction -> {
          if (!it.isSelected(e)) return false
        }
        else -> {
          assert(false) { "Unexpected action type: $it" }
        }
      }
    }
    return true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
