package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rider.plugins.unreal.UnrealHost

fun AnActionEvent.getHost(): UnrealHost? {
    val project = project ?: return null
    return UnrealHost.getInstance(project)
}