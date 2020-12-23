package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.plugins.unreal.model.PlayState
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.RdRiderModel
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution

class UnrealHost(project: Project) : LifetimedProjectComponent(project) {
    companion object {
        fun getInstance(project: Project): UnrealHost = project.getComponent(UnrealHost::class.java)
    }

    val logger = Logger.getInstance(UnrealHost::class.java)

    var playState: PlayState = PlayState.Idle
    var playMode: Int = 0

    internal val model = project.solution.rdRiderModel
    val isUnrealEngineSolution:Boolean
        get() = model.isUnrealEngineSolution.value
    val isConnectedToUnrealEditor:Boolean
        get() = model.isConnectedToUnrealEditor.value

    fun <R> performModelAction(action:(RdRiderModel)->R) {
        action(model)
    }


}
