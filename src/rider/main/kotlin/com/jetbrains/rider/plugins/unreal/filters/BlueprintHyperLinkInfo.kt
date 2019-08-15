package com.jetbrains.rider.plugins.unreal.filters

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info

class BlueprintHyperLinkInfo : HyperlinkInfo {
    override fun navigate(project: Project) {
        getLogger<BlueprintHyperLinkInfo>().info { "BlueprintHyperLinkInfo:navigate" }
    }

}