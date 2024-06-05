package com.jetbrains.rider.plugins.unreal.debugger

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.frame.XStackFrame
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.UnrealPluginUsagesCollector
import com.jetbrains.rider.plugins.unreal.debugger.frames.BlueprintFrame
import com.jetbrains.rider.plugins.unreal.debugger.frames.StubBlueprintFrame
import com.jetbrains.rider.plugins.unreal.debugger.frames.UnrealExternalCodeFrame
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogPanelSettings
import com.jetbrains.rider.projectView.solution
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class BlueprintStackTransformer {
  companion object {
    private val myInstanceCounter: AtomicInteger = AtomicInteger(0)
  }

  private var myOverallTransformActivity: StructuredIdeActivity? = null
  private var myBatchNumber: Int = 0
  private var myRiderModuleIsAvailable: Boolean? = null
  private var myProcess: CidrDebugProcess? = null
  private var myPreviousFrame: XStackFrame? = null
  private var myObsolescent: Obsolescent? = null
  private var myProject: Project? = null
  private var myCachedBlueprintCallstack: MutableList<BlueprintCallFrame>? = null

  @NlsSafe
  private var myCachedErrorForBlueprintCallstack: String? = null
  private var myTopFrame: XStackFrame? = null
  private var myUnrealEngineLocation: String? = null

  private var myLastCollapsedFrame: UnrealExternalCodeFrame? = null

  private val myInstanceNumber: Int = myInstanceCounter.incrementAndGet()

  fun beginTransformation(obsolescent: Obsolescent, project: Project, process: CidrDebugProcess, isSupportModuleAvailable: Boolean) {
    UnrealDebuggerLogger.logger.debug {
      "Begin transformation of blueprint stack frames. Project: ${project.name}" +
      " process: ${process.runParameters}" +
      " isSupportModuleAvailable: ${isSupportModuleAvailable}" +
      " myInstanceNumber: ${myInstanceNumber}"
    }
    myPreviousFrame = null
    myCachedBlueprintCallstack = null
    myCachedErrorForBlueprintCallstack = null
    myTopFrame = null

    myRiderModuleIsAvailable = isSupportModuleAvailable

    myLastCollapsedFrame = null

    myObsolescent = obsolescent
    myProject = project
    myProcess = process

    myUnrealEngineLocation = project.solution.unrealModel.unrealEngineLocation.valueOrNull
    if (myUnrealEngineLocation == null) {
      UnrealDebuggerLogger.logger.warn("Unreal Engine location is not set, project: ${project.name}")
    }

    propertyInitializedInvariant()

    myBatchNumber = 0
    myOverallTransformActivity = UnrealPluginUsagesCollector.startBlueprintStackTransformActivity(project, isSupportedPlatform(),
                                                                                                  isSupportModuleAvailable,
                                                                                                  isBlueprintCallstackEnabled(),
                                                                                                  isShowUnrealFramesEnabled())
  }

  fun endTransformation() {
    UnrealDebuggerLogger.logger.debug { "End transformation of blueprint stack frames. myInstanceNumber: ${myInstanceNumber}" }
    myOverallTransformActivity?.finished()
  }

  private fun propertyInitializedInvariant() {
    assert(myProject != null)
    assert(myProcess != null)
    assert(myObsolescent != null)
  }

  fun transform(stackFrames: List<XStackFrame?>): CompletableFuture<List<XStackFrame?>> {
    UnrealDebuggerLogger.logger.debug {
      "Transforming blueprint stack frames. myInstanceNumber: ${myInstanceNumber}" +
      " stackFrames: ${stackFrames.size}"
    }

    myBatchNumber++
    val transformActivity = UnrealPluginUsagesCollector.startBlueprintStackTransformBatchActivity(myProject, myOverallTransformActivity,
                                                                                                  myBatchNumber, stackFrames.size)

    return transformInternalAsync(stackFrames, transformActivity).handle { okResult, exception ->
      transformActivity?.finished()

      if (exception != null) {
        throw exception
      }

      okResult
    }
  }

  private fun transformInternalAsync(stackFrames: List<XStackFrame?>,
                                     transformActivity: StructuredIdeActivity?): CompletableFuture<List<XStackFrame?>> {
    if (!isSupportedPlatform()) return CompletableFuture.completedFuture(stackFrames)

    if (myProject == null || (!isBlueprintCallstackEnabled() && isShowUnrealFramesEnabled())) {
      return CompletableFuture.completedFuture(stackFrames)
    }

    propertyInitializedInvariant()

    if (myTopFrame == null) {
      myTopFrame = stackFrames.firstOrNull()
    }

    val stackWithInjectedBlueprint = injectBlueprintFunctions(stackFrames, transformActivity)
    return stackWithInjectedBlueprint.thenApply(::collapseExternalCodeFrames)
  }

  private fun collapseExternalCodeFrames(stackFrames: List<XStackFrame?>): List<XStackFrame?> {
    if (isShowUnrealFramesEnabled() || myUnrealEngineLocation == null) {
      return stackFrames
    }
    val frames = mutableListOf<XStackFrame?>()
    var exCodeFramesCount = 0

    var index = 0
    var equalityObject: Any? = null
    for (frame in stackFrames) {
      index++

      if (shouldFrameBeCollapsed(frame)) {
        exCodeFramesCount++
        equalityObject = frame?.equalityObject
        continue
      }

      if (exCodeFramesCount > 0) {

        updateOrAddCollapsedFrame(frames, exCodeFramesCount, equalityObject ?: index)
        exCodeFramesCount = 0
        equalityObject = null
      }

      frames.add(frame)
      myLastCollapsedFrame = null
    }
    index++

    if (exCodeFramesCount > 0) {
      updateOrAddCollapsedFrame(frames, exCodeFramesCount, equalityObject ?: index)
    }

    return frames
  }

  private fun updateOrAddCollapsedFrame(frames: MutableList<XStackFrame?>, newCollapsedCount: Int, equalityObject: Any) {
    if (myLastCollapsedFrame != null) {
      myLastCollapsedFrame!!.collapsedFramesCount += newCollapsedCount
    }
    else {
      val newCollapsedFrame = UnrealExternalCodeFrame(equalityObject, newCollapsedCount)
      frames.add(newCollapsedFrame)
      myLastCollapsedFrame = newCollapsedFrame
    }
  }

  private fun shouldFrameBeCollapsed(frame: XStackFrame?): Boolean {

    if (frame == myTopFrame) {
      return false
    }

    if (frame !is CidrStackFrame) {
      return false
    }

    if (frame.sourcePosition?.file?.path?.startsWith(myUnrealEngineLocation!!, ignoreCase = true) == true) {
      return true
    }

    return false
  }

  private fun initializeBlueprintDataForStack(frame: CidrStackFrame, parentActivity: StructuredIdeActivity?): CompletableFuture<Void> {

    var featureResult: CompletableFuture<Void> = CompletableFuture.completedFuture(null)

    if (myRiderModuleIsAvailable == false) {
      myCachedErrorForBlueprintCallstack = UnrealLinkBundle.message(
        "RiderLink.Unreal.Debugger.BlueprintCallstack.Blueprint.StubFrame.InstallRiderLink")
      return featureResult
    }

    if (myCachedBlueprintCallstack != null) {
      return featureResult
    }

    if (myCachedErrorForBlueprintCallstack != null) {
      return featureResult
    }

    featureResult = myProcess!!.postCommand { driver ->
      if (myObsolescent?.isObsolete == true) return@postCommand

      val activity = UnrealPluginUsagesCollector.startBlueprintStackGettingDataActivity(myProject, parentActivity)

      val bpStackData: String
      try {
        bpStackData = driver.executeInterpreterCommand(frame.threadId, frame.frameIndex,
                                                       "jb_unreal_blueprint_get_stack ${frame.thread.tid}").trim()
      }
      finally {
        activity?.finished()
      }

      when {
        bpStackData.startsWith("NONE_BP_FRAMES") || bpStackData.startsWith("ERROR_BP_FRAMES") || bpStackData.isEmpty() -> {
          myCachedErrorForBlueprintCallstack = UnrealLinkBundle.message(
            "RiderLink.Unreal.Debugger.BlueprintCallstack.Blueprint.StubFrame.UnspecifiedError")
        }
        bpStackData.startsWith("ERROR_AV_EXCEPTION") -> {
          myCachedErrorForBlueprintCallstack = UnrealLinkBundle.message(
            "RiderLink.Unreal.Debugger.BlueprintCallstack.Blueprint.StubFrame.EvaluationFailed")
        }
        else -> {
          myCachedBlueprintCallstack = decodeBlueprintCallFrames(bpStackData)
        }
      }
    }

    return featureResult

  }

  private fun processStackFramesSynchronously(stackFrames: List<XStackFrame?>): List<XStackFrame?> {
    if (myCachedBlueprintCallstack != null) {
      return addBlueprintFramesToStack(stackFrames)
    }

    return addBlueprintStubFramesToStack(stackFrames)
  }

  private fun addBlueprintFramesToStack(stackFrames: List<XStackFrame?>): MutableList<XStackFrame?> {
    val result = mutableListOf<XStackFrame?>()

    var bpIndex = 0
    for (current in stackFrames) {
      if (current !is CidrStackFrame) {
        result.add(current)
        continue
      }

      bpIndex = createBlueprintFrame(current.equalityObject!!, bpIndex, myCachedBlueprintCallstack!!, current.frameIndex, result)

      result.add(current)
    }
    return result
  }

  private fun injectBlueprintFunctions(stackFrames: List<XStackFrame?>,
                                       parentActivity: StructuredIdeActivity?): CompletableFuture<List<XStackFrame?>> {
    if (myProject == null || !isBlueprintCallstackEnabled()) {
      return CompletableFuture.completedFuture(stackFrames)
    }

    if (myObsolescent?.isObsolete == true) {
      return CompletableFuture.completedFuture(stackFrames)
    }

    if (!isProcessingNeeds(stackFrames)) {
      myPreviousFrame = stackFrames.lastOrNull()
      return CompletableFuture.completedFuture(stackFrames)
    }

    assert(stackFrames.isNotEmpty())

    val firstFrame = stackFrames.firstOrNull { it is CidrStackFrame } as? CidrStackFrame ?: return CompletableFuture.completedFuture(
      stackFrames)

    return initializeBlueprintDataForStack(firstFrame, parentActivity).thenApply { processStackFramesSynchronously(stackFrames) }
  }

  private fun addBlueprintStubFramesToStack(stackFrames: List<XStackFrame?>): MutableList<XStackFrame?> {
    assert(myCachedErrorForBlueprintCallstack != null)

    val resultWithStubs = mutableListOf<XStackFrame?>()

    stackFrames.forEachIndexed { index, currentFrame ->
      var found = false
      if (currentFrame is CidrStackFrame && index > 0) {
        val previousCidrFrame = stackFrames[index - 1]
        if (BlueprintCallstackFrameCompatibilityMatcher.matchFrames(currentFrame, previousCidrFrame).isMatched) {
          found = true
        }
      }

      resultWithStubs.add(currentFrame)
      if (found) {
        val stubFrame = StubBlueprintFrame(currentFrame!!.equalityObject!!, myCachedErrorForBlueprintCallstack!!)
        resultWithStubs.add(stubFrame)
      }
    }
    return resultWithStubs
  }

  private fun createBlueprintFrame(frameEqualityObject: Any,
                                   bpIndex: Int,
                                   decodeBlueprintCallFrames: List<BlueprintCallFrame>,
                                   frameIndex: Int,
                                   result: MutableList<XStackFrame?>): Int {
    var bpIndexLocal = bpIndex
    if (bpIndexLocal >= decodeBlueprintCallFrames.size) return bpIndexLocal

    val bpFrame = decodeBlueprintCallFrames[bpIndexLocal]

    if (frameIndex != bpFrame.originalFrameIndex) return bpIndexLocal

    var functionFullName = bpFrame.functionFullName

    @Suppress("SpellCheckingInspection") val ubergraphConst = "ExecuteUbergraph"

    if (functionFullName.contains("${ubergraphConst}_${bpFrame.objectName}")) {
      functionFullName = "Event Graph"
    }

    result.add(BlueprintFrame(frameEqualityObject, bpFrame.objectName, bpFrame.functionDisplayName, functionFullName))

    bpIndexLocal++

    return bpIndexLocal
  }

  private fun decodeBlueprintCallFrames(input: String): MutableList<BlueprintCallFrame> {
    if (input.isEmpty()) return mutableListOf()
    val result = mutableListOf<BlueprintCallFrame>()
    input.splitToSequence("<<<!!!").forEach {
      val parts = it.split("^^^")
      if (parts.size >= 4) {
        result.add(BlueprintCallFrame(parts[0].toInt(), parts[1], parts[2], parts[3]))
      }
      else {
        throw IllegalArgumentException("Invalid line format: $it")
      }
    }
    return result
  }

  private fun isProcessingNeeds(stackFrames: List<XStackFrame?>): Boolean {
    if (stackFrames.isEmpty()) return false

    if (BlueprintCallstackFrameCompatibilityMatcher.matchFrames(stackFrames.first(), myPreviousFrame).isMatched) {
      return true
    }

    for (i in 1 until stackFrames.size) {
      if (BlueprintCallstackFrameCompatibilityMatcher.matchFrames(stackFrames[i], stackFrames[i - 1]).isMatched) {
        return true
      }
    }
    return false
  }

  private fun isBlueprintCallstackEnabled(): Boolean {
    assert(myProject != null)

    return UnrealLogPanelSettings.getInstance(myProject!!).showBlueprintCallstack
  }

  private fun isShowUnrealFramesEnabled(): Boolean {
    assert(myProject != null)

    return UnrealLogPanelSettings.getInstance(myProject!!).showUnrealFrames
  }

  private fun isSupportedPlatform(): Boolean {
    return SystemInfo.isWindows
  }

  data class BlueprintCallFrame(val originalFrameIndex: Int,
                                val functionFullName: String,
                                val objectName: String,
                                val functionDisplayName: String)
}
