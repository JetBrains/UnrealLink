package com.jetbrains.rider.plugins.unreal.debugger.frames

open class UnrealFrameBase : com.intellij.xdebugger.frame.XStackFrame() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val frame: UnrealFrameBase = other as UnrealFrameBase

    return frame.equalityObject == equalityObject
  }

  override fun hashCode(): Int {
    return equalityObject.hashCode()
  }
}