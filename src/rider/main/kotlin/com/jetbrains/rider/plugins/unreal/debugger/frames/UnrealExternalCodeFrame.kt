package com.jetbrains.rider.plugins.unreal.debugger.frames

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.debugger.DotNetStackFrame
import com.jetbrains.rider.debugger.RiderDebuggerBundle

class UnrealExternalCodeFrame(private val baseFrameEqualityObject: Any, var collapsedFramesCount: Int) : UnrealFrameBase() {

  companion object {
    @NlsSafe
    private val myCodePrefix = "${UnrealLinkBundle.message("RiderLink.Unreal.Debugger.BlueprintCallstack.UnrealEngineCode.title")} "

    @NlsSafe
    private val myCodeSuffix = UnrealLinkBundle.message("RiderLink.Unreal.Debugger.BlueprintCallstack.UnrealEngineCode.suffix")
  }

  override fun getEqualityObject(): Any {
    return Pair.create(baseFrameEqualityObject, collapsedFramesCount)
  }

  @Suppress("ConvertToStringTemplate")
  override fun customizePresentation(component: ColoredTextContainer) {
    component.append("[", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DotNetStackFrame.ExternalCodeSecondColor))
    component.append(myCodePrefix, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DotNetStackFrame.ExternalCodeSecondColor))
    component.append(collapsedFramesCount.toString() + " ",
                     SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, DotNetStackFrame.ExternalCodeSecondColor))
    component.append(StringUtil.pluralize(RiderDebuggerBundle.message("ExternalCodeMetaFrame.frame.text"), collapsedFramesCount) + " ",
                     SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DotNetStackFrame.ExternalCodeSecondColor))
    component.append(myCodeSuffix + "]", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DotNetStackFrame.ExternalCodeSecondColor))
  }
}