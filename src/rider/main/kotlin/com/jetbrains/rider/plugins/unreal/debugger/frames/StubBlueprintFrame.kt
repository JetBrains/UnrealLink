package com.jetbrains.rider.plugins.unreal.debugger.frames

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes

class StubBlueprintFrame(private val baseFrameEqualityObject: Any, @NlsSafe private val titleResource: String) : UnrealFrameBase() {

  override fun getEqualityObject(): Any {
    return Pair.create(baseFrameEqualityObject, titleResource)
  }

  override fun customizePresentation(component: ColoredTextContainer) {

    component.append(blueprintTitlePrefix, SimpleTextAttributes.GRAYED_ATTRIBUTES)

    component.append(titleResource, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
  }
}