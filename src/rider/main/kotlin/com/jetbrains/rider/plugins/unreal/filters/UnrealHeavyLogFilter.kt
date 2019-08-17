package com.jetbrains.rider.plugins.unreal.filters

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.FilterMixin
import com.intellij.execution.filters.FilterMixin.AdditionalHighlight
import com.intellij.execution.filters.UrlFilter
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.jetbrains.rd.framework.IRdCall
import com.jetbrains.rd.framework.RdTaskResult
import com.jetbrains.rd.util.reactive.ISignal
import com.jetbrains.rdclient.daemon.HighlighterRegistrationHost
import com.jetbrains.rider.model.FString
import com.jetbrains.rider.plugins.unreal.toolWindow.messageStartPosition
import com.jetbrains.rider.stacktrace.filters.RiderHeavyExceptionFilter
import com.jetbrains.rider.util.idea.application
import com.jetbrains.rider.util.idea.getLogger
import com.jetbrains.rider.util.idea.lifetime
import java.awt.Color

class UnrealHeavyLogFilter(val project: Project, private val registrationHost: HighlighterRegistrationHost,
                           val filter: IRdCall<FString, Boolean>, private val navigate: ISignal<FString>) : Filter, FilterMixin {
    companion object {
        private val logger = getLogger<RiderHeavyExceptionFilter>()
    }

    private val urlFilter = UrlFilter()

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        return urlFilter.applyFilter(line, entireLength)
    }

    override fun shouldRunHeavy() = true

    override fun getUpdateMessage() = "Looking for valid Blueprint"

    override fun applyHeavyFilter(copiedFragment: Document, startOffset: Int, startLineNumber: Int, consumer: Consumer<AdditionalHighlight>) {
        //heavy filters in tests can try to get access via Vfs to files outside of allowed roots
        if (application.isUnitTestMode) {
            return
        }


        val text = copiedFragment.charsSequence

        val scriptCallStackPrefix = "Script call stack:"
        if (text.startsWith(scriptCallStackPrefix, text.messageStartPosition())) {
            //todo folding
            val textAttributes = ConsoleViewContentType.ERROR_OUTPUT_KEY.defaultAttributes
            consumer.consume(AdditionalHighlight(listOf(
                    Filter.ResultItem(text.messageStartPosition(), text.length, null, textAttributes))))
        }
        BlueprintParser.parse(text).forEach {
            //todo change regex and send task immediately
            val task = filter.start(FString(it.value))
            task.result.advise(project.lifetime) { rdTaskResult ->
                when (rdTaskResult) {
                    is RdTaskResult.Success -> {
                        val blueprintHyperLinkInfo = BlueprintHyperLinkInfo(navigate, it.value)
                        val resultItems = Filter.ResultItem(it.range.first + startOffset, it.range.last + startOffset, blueprintHyperLinkInfo, true)
                        consumer.consume(AdditionalHighlight(arrayListOf(resultItems)))
                    }
                    is RdTaskResult.Cancelled -> {
                        logger.debug("Request has been canceled")
                    }
                    is RdTaskResult.Fault -> {
                        logger.error("Request has faulted")
                    }
                }
            }
        }
    }
}
