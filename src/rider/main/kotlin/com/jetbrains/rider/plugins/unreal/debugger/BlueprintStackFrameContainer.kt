package com.jetbrains.rider.plugins.unreal.debugger

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.frame.XStackFrameContainerEx
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess

class BlueprintStackFrameContainer(private val myOriginalContainer: XExecutionStack.XStackFrameContainer,
                                   private val bluePrintStackTransformer: BluePrintStackTransformer,
                                   isSupportModuleAvailable: Boolean,
                                   project: Project,
                                   myProcess: CidrDebugProcess) : XStackFrameContainerEx {

  init {
    bluePrintStackTransformer.beginTransformation(myOriginalContainer, project, myProcess, isSupportModuleAvailable)
  }

  override fun hashCode(): Int = myOriginalContainer.hashCode()
  override fun isObsolete(): Boolean = myOriginalContainer.isObsolete
  override fun toString(): String = myOriginalContainer.toString()
  override fun equals(other: Any?): Boolean = myOriginalContainer == other
  override fun errorOccurred(errorMessage: String) = myOriginalContainer.errorOccurred(errorMessage)

  override fun addStackFrames(stackFrames: MutableList<out XStackFrame>, toSelect: XStackFrame?, last: Boolean) {
    bluePrintStackTransformer.transform(stackFrames).thenApply {
      when (myOriginalContainer) {
        is XStackFrameContainerEx -> myOriginalContainer.addStackFrames(it, toSelect, last)
        else -> myOriginalContainer.addStackFrames(it, last)
      }
    }
  }

  override fun addStackFrames(stackFrames: MutableList<out XStackFrame>, last: Boolean) {
    addStackFrames(stackFrames, null, last)
  }

}
