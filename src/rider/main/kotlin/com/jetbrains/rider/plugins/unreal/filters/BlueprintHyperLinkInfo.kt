package com.jetbrains.rider.plugins.unreal.filters

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.reactive.ISignal
import com.jetbrains.rider.model.BlueprintStruct

class BlueprintHyperLinkInfo(private val navigation: ISignal<BlueprintStruct>, private val struct: BlueprintStruct) : HyperlinkInfo {
    companion object {
        val logger = getLogger<BlueprintHyperLinkInfo>()
    }

    override fun navigate(project: Project) {
        logger.info { "BlueprintHyperLinkInfo:navigate by link=$struct" }

        navigation.fire(struct)
    }

}