package com.jetbrains.rider.plugins.unreal.debugger

import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrExecutionStack
import com.jetbrains.cidr.execution.debugger.CidrSuspensionCause
import com.jetbrains.cidr.execution.debugger.backend.LLFrame
import com.jetbrains.cidr.execution.debugger.backend.LLThread
import com.jetbrains.cidr.execution.debugger.backend.LLValue

class BlueprintExecutionStack(process: CidrDebugProcess,
                              thread: LLThread,
                              frame: LLFrame?,
                              current: Boolean,
                              cause: CidrSuspensionCause?,
                              returnValue: LLValue?,
                              private val isSupportModuleAvailable: Boolean,
                              private val bluePrintStackTransformer: BluePrintStackTransformer) : CidrExecutionStack(process, thread, frame,
                                                                                                                     current, cause,
                                                                                                                     returnValue) {

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
    val myContainer = BlueprintStackFrameContainer(container, bluePrintStackTransformer, isSupportModuleAvailable, myProcess.project,
                                                   myProcess)

    super.computeStackFrames(firstFrameIndex, myContainer)
  }
}

