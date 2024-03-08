package com.jetbrains.rider.plugins.unreal.debugger.actions

import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings

class ShowUnrealFramesAction : UnrealToggleBaseAction() {

  override fun getToggleActionText(settings: UnrealLogPanelSettings): String =
    "action.RiderLink.Unreal.Debugger.UnrealFrames.show.text"

  override fun getIsSelected(settings: UnrealLogPanelSettings): Boolean = settings.showUnrealFrames

  override fun setSelectedAction(settings: UnrealLogPanelSettings, selected: Boolean) {
    settings.showUnrealFrames = selected
  }
}
