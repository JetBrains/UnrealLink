package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.model.RdRiderModel
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.util.idea.getComponent

class UnrealHost(project: Project) : LifetimedProjectComponent(project) {
    companion object {
        fun getInstance(project: Project) = project.getComponent<UnrealHost>()
    }

    internal val model = project.solution.rdRiderModel
    val isUnrealEngineSolution:Boolean
            get() = model.isUnrealEngineSolution.value

    fun <R> performModelAction(action:(RdRiderModel)->R) {
        action(model)
    }


}
