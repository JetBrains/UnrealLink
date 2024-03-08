package com.jetbrains.rider.plugins.unreal.debugger.actions

import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings


class ShowBlueprintFunctionsAction : UnrealToggleBaseAction() {

  override fun getToggleActionText(settings: UnrealLogPanelSettings): String =
    "action.RiderLink.Unreal.Debugger.BlueprintCallstack.show.text"

  override fun getIsSelected(settings: UnrealLogPanelSettings): Boolean = settings.showBlueprintCallstack

  override fun setSelectedAction(settings: UnrealLogPanelSettings, selected: Boolean) {
    settings.showBlueprintCallstack = selected
  }
}
