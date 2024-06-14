package com.jetbrains.rider.plugins.unreal.debugger.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.actions.getUnrealHost
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings


abstract class UnrealToggleBaseAction : DumbAwareToggleAction() {
  var isVisible: Boolean = true
    private set

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun isSoftMultiChoice(): Boolean = false

  override fun update(e: AnActionEvent) {
    super.update(e)

    if (!Registry.`is`("rider.cpp.enable.debugger.unreal.blueprint-callstack")
        || !SystemInfo.isWindows) {
      e.presentation.isEnabledAndVisible = false
      isVisible = e.presentation.isVisible
      return
    }

    val project = e.project ?: return
    if (!isHostAvailable(e)) return

    e.presentation.isEnabledAndVisible = true

    val settings = UnrealLogPanelSettings.getInstance(project)
    e.presentation.text = UnrealLinkBundle.message(getToggleActionText(settings))

    isVisible = e.presentation.isVisible
  }

  private fun isHostAvailable(e: AnActionEvent): Boolean = e.getUnrealHost() != null

  @NlsActions.ActionText
  abstract fun getToggleActionText(settings: UnrealLogPanelSettings): String

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val settings = UnrealLogPanelSettings.getInstance(project)
    return getIsSelected(settings)
  }

  abstract fun getIsSelected(settings: UnrealLogPanelSettings): Boolean

  override fun setSelected(e: AnActionEvent, selected: Boolean) {
    val project = e.project ?: return
    val settings = UnrealLogPanelSettings.getInstance(project)
    setSelectedAction(settings, selected)

    XDebuggerUtilImpl.rebuildAllSessionsViews(e.project)
  }

  abstract fun setSelectedAction(settings: UnrealLogPanelSettings, selected: Boolean)
}