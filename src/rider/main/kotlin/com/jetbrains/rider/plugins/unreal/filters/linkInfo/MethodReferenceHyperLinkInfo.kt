package com.jetbrains.rider.plugins.unreal.filters.linkInfo

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.impl.startAndAdviseSuccess
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rider.model.MethodReference
import com.jetbrains.rider.model.RdRiderModel

class MethodReferenceHyperLinkInfo(private val model: RdRiderModel,
                                   private val methodReference: MethodReference) : HyperlinkInfo {
    companion object {
        val logger = getLogger<BlueprintFunctionHyperLinkInfo>()
    }

    override fun navigate(project: Project) {
        UnrealClassHyperLinkInfo.logger.info { "checking methodReference '$methodReference'" }
        model.isMethodReference.startAndAdviseSuccess(methodReference) {
            if (!it) return@startAndAdviseSuccess

            logger.info { "navigate to '$methodReference'" }
            model.navigateToMethod.fire(methodReference)
        }
    }
}