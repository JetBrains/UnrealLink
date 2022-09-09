package com.jetbrains.rider.settings

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.toolWindow.log.UnrealLogConsoleViewContentType
import icons.UnrealIcons
import javax.swing.Icon


@Suppress("UnstableApiUsage")
class UnrealLogColorSettingsPage : ColorSettingsPage {
    companion object {
        // These names are from UE enumeration and should not be localized
        private const val FATAL: @NlsSafe String = "Fatal"
        private const val ERROR: @NlsSafe String = "Error"
        private const val WARNING: @NlsSafe String = "Warning"
        private const val DISPLAY: @NlsSafe String = "Display"
        private const val LOG: @NlsSafe String = "Log"
        private const val VERBOSE: @NlsSafe String = "Verbose"
        private const val VERY_VERBOSE: @NlsSafe String = "VeryVerbose"

        private val ATTR_DESCRIPTORS: Array<AttributesDescriptor> = arrayOf(
                AttributesDescriptor(FATAL, UnrealLogConsoleViewContentType.UE_LOG_FATAL_KEY),
                AttributesDescriptor(ERROR, UnrealLogConsoleViewContentType.UE_LOG_ERROR_KEY),
                AttributesDescriptor(WARNING, UnrealLogConsoleViewContentType.UE_LOG_WARNING_KEY),
                AttributesDescriptor(DISPLAY, UnrealLogConsoleViewContentType.UE_LOG_DISPLAY_KEY),
                AttributesDescriptor(LOG, UnrealLogConsoleViewContentType.UE_LOG_LOG_KEY),
                AttributesDescriptor(VERBOSE, UnrealLogConsoleViewContentType.UE_LOG_VERBOSE_KEY),
                AttributesDescriptor(VERY_VERBOSE, UnrealLogConsoleViewContentType.UE_LOG_VERY_VERBOSE_KEY),
        )

        private val ADDITIONAL_HIGHLIGHT_DESCRIPTORS: Map<String, TextAttributesKey> = mapOf(
                "fatal" to UnrealLogConsoleViewContentType.UE_LOG_FATAL_KEY,
                "error" to UnrealLogConsoleViewContentType.UE_LOG_ERROR_KEY,
                "warning" to UnrealLogConsoleViewContentType.UE_LOG_WARNING_KEY,
                "display" to UnrealLogConsoleViewContentType.UE_LOG_DISPLAY_KEY,
                "log" to UnrealLogConsoleViewContentType.UE_LOG_LOG_KEY,
                "verbose" to UnrealLogConsoleViewContentType.UE_LOG_VERBOSE_KEY,
                "very_verbose" to UnrealLogConsoleViewContentType.UE_LOG_VERY_VERBOSE_KEY,
        )
    }

    override fun getDisplayName(): String {
        return UnrealLinkBundle.message("color.settings.unreal.console.name")
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        return ATTR_DESCRIPTORS
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return emptyArray()
    }

    override fun getHighlighter(): SyntaxHighlighter {
        return PlainSyntaxHighlighter()
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> {
        return ADDITIONAL_HIGHLIGHT_DESCRIPTORS
    }

    override fun getDemoText(): String {
        return """
            
            <fatal>UE_LOG(LogCategory, Fatal, TEXT("Message"));</fatal>
            <error>UE_LOG(LogCategory, Error, TEXT("Message"));</error>
            <warning>UE_LOG(LogCategory, Warning, TEXT("Message"));</warning>
            <display>UE_LOG(LogCategory, Display, TEXT("Message"));</display>
            <log>UE_LOG(LogCategory, Log, TEXT("Message"));</log>
            <verbose>UE_LOG(LogCategory, Verbose, TEXT("Message"));</verbose>
            <very_verbose>UE_LOG(LogCategory, VeryVerbose, TEXT("Message"));</very_verbose>
        """.trimIndent()
    }

    override fun getIcon(): Icon = UnrealIcons.Status.UnrealEngineLogo

    override fun customizeColorScheme(scheme: EditorColorsScheme): EditorColorsScheme {
        return ConsoleViewUtil.updateConsoleColorScheme(scheme)
    }
}