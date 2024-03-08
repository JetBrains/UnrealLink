package com.jetbrains.rider.plugins.unreal.debugger

data class BlueprintCallstackFrameMatchResult(val isMatched: Boolean, val modulePrefix: String) {
  companion object {
    val NotMatched = BlueprintCallstackFrameMatchResult(false, "")
  }
}