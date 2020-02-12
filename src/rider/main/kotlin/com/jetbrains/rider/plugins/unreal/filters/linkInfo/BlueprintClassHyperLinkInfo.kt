package com.jetbrains.rider.plugins.unreal.filters.linkInfo

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.reactive.ISignal
import com.jetbrains.rider.model.BlueprintReference

class BlueprintClassHyperLinkInfo(private val navigation: ISignal<BlueprintReference>, private val struct: BlueprintReference) : HyperlinkInfo {
    companion object {
        val logger = getLogger<BlueprintClassHyperLinkInfo>()
    }

    override fun navigate(project: Project) {
        logger.info { "navigate by $struct" }

        navigation.fire(struct)
    }

}
