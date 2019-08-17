package com.jetbrains.rider.plugins.unreal.filters

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.reactive.ISignal
import com.jetbrains.rider.model.FString

class BlueprintHyperLinkInfo(private val navigation: ISignal<FString>, private val link : String) : HyperlinkInfo {
    override fun navigate(project: Project) {
        getLogger<BlueprintHyperLinkInfo>().info { "BlueprintHyperLinkInfo:navigate by link=$link" }

        navigation.fire(FString(link))
    }

}