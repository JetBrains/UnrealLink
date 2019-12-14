package com.jetbrains.rider.plugins.unreal.filters.linkInfo

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.reactive.ISignal
import com.jetbrains.rider.model.MethodReference

class MethodReferenceHyperLinkInfo(private val navigation: ISignal<MethodReference>, private val methodReference: MethodReference) : HyperlinkInfo {
    companion object {
        val logger = getLogger<BlueprintFunctionHyperLinkInfo>()
    }

    override fun navigate(project: Project) {
        logger.info { "navigate by $methodReference" }

        navigation.fire(methodReference)
    }
}