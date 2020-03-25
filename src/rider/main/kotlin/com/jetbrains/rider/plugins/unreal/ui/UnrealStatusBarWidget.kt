@file:Suppress("DEPRECATION")
package com.jetbrains.rider.plugins.unreal.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.jetbrains.rider.plugins.unreal.UnrealHost

class UnrealStatusBarWidget: StatusBarWidgetFactory {
    override fun getId() = UnrealStatusBarIcon.StatusBarIconId
    override fun isAvailable(project: Project) = UnrealHost.getInstance(project).isUnrealEngineSolution
    override fun canBeEnabledOn(statusBar: StatusBar) = true
    override fun getDisplayName() = "Unity Editor connection"
    override fun disposeWidget(widget: StatusBarWidget) {}
    override fun createWidget(project: Project) = UnrealStatusBarIcon(project)
}