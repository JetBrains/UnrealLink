package com.jetbrains.rider.plugins.unreal.debugger.frames

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes

class BlueprintFrame(private val baseFrameEqualityObject: Any,
                     @NlsSafe private val objectName: String,
                     @NlsSafe private val functionDisplayName: String,
                     @NlsSafe private val functionFullName: String) : UnrealFrameBase() {

  override fun getEqualityObject(): Any {
    return Pair.create(baseFrameEqualityObject, objectName + functionDisplayName + functionFullName)
  }

  override fun customizePresentation(component: ColoredTextContainer) {
    component.append(blueprintTitlePrefix, SimpleTextAttributes.GRAYED_ATTRIBUTES)

    component.append("$objectName: ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    component.append("$functionDisplayName ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    component.append(" $functionFullName", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
  }

}

