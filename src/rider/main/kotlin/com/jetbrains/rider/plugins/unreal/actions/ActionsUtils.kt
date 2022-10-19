package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rider.plugins.unreal.UnrealHost

fun AnActionEvent.getUnrealHost(): UnrealHost? {
    val project = project ?: return null
    return UnrealHost.getInstance(project)
}

fun forceTriggerUIUpdate() {
    // Action button presentations won't be updated if no events occur
    // (e.g. mouse isn't moving, keys aren't being pressed).
    // In that case emulating activity will help:
    ActivityTracker.getInstance().inc()
}
