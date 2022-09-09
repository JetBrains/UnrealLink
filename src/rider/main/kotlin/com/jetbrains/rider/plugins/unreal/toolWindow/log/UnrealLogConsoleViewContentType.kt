package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.annotations.NonNls

class UnrealLogConsoleViewContentType(@NonNls name: String, textAttributesKey: TextAttributesKey)
    : ConsoleViewContentType(name, textAttributesKey) {

    companion object {
        val UE_LOG_FATAL_KEY = TextAttributesKey.createTextAttributesKey("UE_LOG_FATAL", LOG_ERROR_OUTPUT_KEY)
        val UE_LOG_ERROR_KEY = TextAttributesKey.createTextAttributesKey("UE_LOG_ERROR", LOG_ERROR_OUTPUT_KEY)
        val UE_LOG_WARNING_KEY = TextAttributesKey.createTextAttributesKey("UE_LOG_WARNING", LOG_WARNING_OUTPUT_KEY)
        val UE_LOG_DISPLAY_KEY = TextAttributesKey.createTextAttributesKey("UE_LOG_DISPLAY", LOG_INFO_OUTPUT_KEY)
        val UE_LOG_LOG_KEY = TextAttributesKey.createTextAttributesKey("UE_LOG_LOG", LOG_INFO_OUTPUT_KEY)
        val UE_LOG_VERBOSE_KEY = TextAttributesKey.createTextAttributesKey("UE_LOG_VERBOSE", LOG_DEBUG_OUTPUT_KEY)
        val UE_LOG_VERY_VERBOSE_KEY = TextAttributesKey.createTextAttributesKey("UE_LOG_VERY_VERBOSE", LOG_DEBUG_OUTPUT_KEY)

        val UE_LOG_FATAL = UnrealLogConsoleViewContentType("UE_LOG_FATAL", UE_LOG_FATAL_KEY)
        val UE_LOG_ERROR = UnrealLogConsoleViewContentType("UE_LOG_ERROR", UE_LOG_ERROR_KEY)
        val UE_LOG_WARNING = UnrealLogConsoleViewContentType("UE_LOG_WARNING", UE_LOG_WARNING_KEY)
        val UE_LOG_DISPLAY = UnrealLogConsoleViewContentType("UE_LOG_DISPLAY", UE_LOG_DISPLAY_KEY)
        val UE_LOG_LOG = UnrealLogConsoleViewContentType("UE_LOG_LOG", UE_LOG_LOG_KEY)
        val UE_LOG_VERBOSE = UnrealLogConsoleViewContentType("UE_LOG_VERBOSE", UE_LOG_VERBOSE_KEY)
        val UE_LOG_VERY_VERBOSE = UnrealLogConsoleViewContentType("UE_LOG_VERY_VERBOSE", UE_LOG_VERY_VERBOSE_KEY)
    }
}