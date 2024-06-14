package com.jetbrains.rider.plugins.unreal.debugger.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareToggleAction
import com.jetbrains.rider.UnrealLinkBundle

class ShowLibraryFramesAction : DumbAwareToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun isSoftMultiChoice(): Boolean = false

  override fun isSelected(e: AnActionEvent): Boolean {
    return !getBaseAction().isSelected(e)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text = UnrealLinkBundle.message("action.RiderLink.Unreal.Debugger.OtherLibrariesFrames.show.text")
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getBaseAction().setSelected(e, !state)
  }

  private fun getBaseAction(): ToggleAction = ActionManager.getInstance().getAction("Debugger.ShowLibraryFrames") as ToggleAction
}