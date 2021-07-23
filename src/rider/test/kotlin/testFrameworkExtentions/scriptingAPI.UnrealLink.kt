package com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions

import com.intellij.execution.RunManagerEx
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import com.jetbrains.rider.test.base.BaseTestWithSolution
import com.jetbrains.rider.test.scriptingApi.startRunConfigurationProcess
import com.jetbrains.rider.test.scriptingApi.stop
import java.time.Duration

fun BaseTestWithSolution.withRunProgram(
    timeout: Duration = Duration.ofSeconds(30),
    action : (Project) -> Unit
) {
    var projectProcess : ProcessHandler? = null
    try {
        val runManagerEx = RunManagerEx.getInstanceEx(project)
        val settings = runManagerEx.selectedConfiguration
            ?: throw AssertionError("No configuration selected")
        projectProcess = startRunConfigurationProcess(project, settings, timeout)
        action(project)
    } finally {
        projectProcess!!.stop()
    }
}
