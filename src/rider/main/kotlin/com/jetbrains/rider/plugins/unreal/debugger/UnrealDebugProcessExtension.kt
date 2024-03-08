package com.jetbrains.rider.plugins.unreal.debugger

import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrExecutionStack
import com.jetbrains.cidr.execution.debugger.CidrSuspensionCause
import com.jetbrains.cidr.execution.debugger.backend.LLFrame
import com.jetbrains.cidr.execution.debugger.backend.LLModule
import com.jetbrains.cidr.execution.debugger.backend.LLThread
import com.jetbrains.cidr.execution.debugger.backend.LLValue
import com.jetbrains.rider.cpp.debugger.RiderCppDebugProcess

class UnrealDebugProcessExtension : com.jetbrains.rider.cpp.debugger.RiderCppDebugProcessExtension {
  private val mySupportModuleIsAvailableKey = com.intellij.openapi.util.Key<Boolean>("riderDebuggingSupportModuleIsAvailable")
  private val myIsUnrealHostKey = com.intellij.openapi.util.Key<Boolean>("riderIsUnrealHost")

  override fun getExecutionStack(process: RiderCppDebugProcess,
                                 thread: LLThread,
                                 frame: LLFrame?,
                                 current: Boolean,
                                 cause: CidrSuspensionCause?,
                                 returnValue: LLValue?): CidrExecutionStack? {
    initIsUnrealHost(process)

    if (!process.getUserData(myIsUnrealHostKey)!!) return null

    val isSupportModuleAvailable = process.getUserData(mySupportModuleIsAvailableKey) ?: false

    return BlueprintExecutionStack(process, thread, frame, current, cause, returnValue, isSupportModuleAvailable, BluePrintStackTransformer())
  }

  override fun handleModulesLoaded(debugProcess: RiderCppDebugProcess, changedModules: MutableList<LLModule>) {
    debugProcess.getUserData(mySupportModuleIsAvailableKey).let {
      if (it == true) return
    }

    if (!containsRiderDebuggerSupportModule(changedModules)) return

    debugProcess.putUserData(mySupportModuleIsAvailableKey, true)
  }

  override fun handleModulesUnloaded(debugProcess: RiderCppDebugProcess, changedModules: MutableList<LLModule>) {
    debugProcess.getUserData(mySupportModuleIsAvailableKey).let {
      if (it != true) return
    }

    if (!containsRiderDebuggerSupportModule(changedModules)) return

    debugProcess.putUserData(mySupportModuleIsAvailableKey, false)
  }

  private fun containsRiderDebuggerSupportModule(modules: MutableList<LLModule>): Boolean {
    return modules.any { it.path.contains("RiderDebuggerSupport", ignoreCase = true) }
  }

  private fun initIsUnrealHost(debugProcess: CidrDebugProcess) {
    debugProcess.getUserData(myIsUnrealHostKey).let {
      if (it != null) return
    }

    val project = debugProcess.project
    val host = com.jetbrains.rider.plugins.unreal.UnrealHost.getInstance(project)
    debugProcess.putUserData(myIsUnrealHostKey, host.isUnrealEngineSolution)

  }
}