package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.project.Project
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rdclient.util.idea.getLogger
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.util.idea.getComponent

class UnrealHost(project: Project) : LifetimedProjectComponent(project) {
    companion object {
        val logger = getLogger<UnrealHost>()
        fun getInstance(project: Project) = project.getComponent<UnrealHost>()
    }

    internal val model = project.solution.rdRiderModel

    init {
        model.testConnection.advise(componentLifetime) {
            logger.info("Connection UE $it")
        }
    }
}