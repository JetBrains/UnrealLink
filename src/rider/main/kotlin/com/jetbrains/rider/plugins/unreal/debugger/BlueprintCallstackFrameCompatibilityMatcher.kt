package com.jetbrains.rider.plugins.unreal.debugger

import com.intellij.xdebugger.frame.XStackFrame
import com.jetbrains.cidr.execution.debugger.CidrStackFrame


class BlueprintCallstackFrameCompatibilityMatcher {
  companion object {
    private const val UE_CORE_MODULE_NAME = "UnrealEditor-CoreUObject.dll"
    private const val UE4_CORE_MODULE_NAME = "UE4Editor-CoreUObject.dll"

    @Suppress("SpellCheckingInspection")
    private const val UE_UFUNCTION_INVOKE_NAME = "UFunction::Invoke(UObject*,FFrame&,void*const)"

    @Suppress("SpellCheckingInspection")
    private const val UE_UOBJECT_PROCESSEVENT_NAME = "UObject::ProcessEvent(UFunction*,void*)"

    @Suppress("SpellCheckingInspection")
    private const val UE_PROCESSLOCALSCRIPTFUNCTION_NAME = "ProcessLocalScriptFunction(UObject*,FFrame&,void*const)"

    fun matchFrames(current: XStackFrame?, previous: XStackFrame?): BlueprintCallstackFrameMatchResult {
      val currentFrame = current as? CidrStackFrame ?: return BlueprintCallstackFrameMatchResult.NotMatched
      val previousFrame = previous as? CidrStackFrame

      val modulePrefix = when (currentFrame.frame.module) {
        UE_CORE_MODULE_NAME -> "UnrealEditor"
        UE4_CORE_MODULE_NAME -> "UE4Editor"
        else -> return BlueprintCallstackFrameMatchResult.NotMatched
      }

      val currentFrameFunction = currentFrame.frame.function.replace(" ", "")
      val previousFrameFunction = previousFrame?.frame?.function?.replace(" ", "")

      return when {
        currentFrameFunction == UE_PROCESSLOCALSCRIPTFUNCTION_NAME -> BlueprintCallstackFrameMatchResult(true, modulePrefix)
        currentFrameFunction == UE_UOBJECT_PROCESSEVENT_NAME && previousFrameFunction == UE_UFUNCTION_INVOKE_NAME -> BlueprintCallstackFrameMatchResult(
          true, modulePrefix)
        else -> BlueprintCallstackFrameMatchResult.NotMatched
      }
    }
  }
}
