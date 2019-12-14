package com.jetbrains.rider.plugins.unreal.filters.linkInfo

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.reactive.ISignal
import com.jetbrains.rider.model.BlueprintClass

class UnrealClassHyperLinkInfo(private val navigation: ISignal<BlueprintClass>, private val struct: BlueprintClass) : HyperlinkInfo {
    companion object {
        val logger = getLogger<UnrealClassHyperLinkInfo>()
    }

    override fun navigate(project: Project) {
        navigation.fire(struct)
    }
}