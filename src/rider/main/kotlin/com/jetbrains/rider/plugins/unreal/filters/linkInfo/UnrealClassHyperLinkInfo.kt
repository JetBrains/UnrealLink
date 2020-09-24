package com.jetbrains.rider.plugins.unreal.filters.linkInfo

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.impl.startAndAdviseSuccess
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rider.model.MethodReference
import com.jetbrains.rider.model.RdRiderModel
import com.jetbrains.rider.model.UClass

class UnrealClassHyperLinkInfo(private val model: RdRiderModel,
                               private val methodReference: MethodReference,
                               private val uClass: UClass) : HyperlinkInfo {
    companion object {
        val logger = getLogger<UnrealClassHyperLinkInfo>()
    }

    override fun navigate(project: Project) {
        logger.info { "checking methodReference '$methodReference'" }
        model.isMethodReference.startAndAdviseSuccess(methodReference) {
            if (!it) return@startAndAdviseSuccess

            logger.info { "navigate to '$uClass'" }
            model.navigateToClass.fire(uClass)
        }
    }
}
