package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.model.PlayState
import com.jetbrains.rider.model.RdRiderModel
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.util.idea.getComponent
import com.jetbrains.rider.util.idea.getLogger

class UnrealHost(project: Project) : LifetimedProjectComponent(project) {
    companion object {
        fun getInstance(project: Project) = project.getComponent<UnrealHost>()
    }

    val logger = getLogger<UnrealHost>()
    var playState: PlayState = PlayState.Idle;

    internal val model = project.solution.rdRiderModel
    val isUnrealEngineSolution:Boolean
        get() = model.isUnrealEngineSolution.value
    val isConnectedToUnrealEditor:Boolean
        get() = model.isConnectedToUnrealEditor.value

    fun <R> performModelAction(action:(RdRiderModel)->R) {
        action(model)
    }


}
