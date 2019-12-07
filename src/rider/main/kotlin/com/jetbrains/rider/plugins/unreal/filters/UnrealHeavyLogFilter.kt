package com.jetbrains.rider.plugins.unreal.filters

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.FilterMixin
import com.intellij.execution.filters.FilterMixin.AdditionalHighlight
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.jetbrains.rd.framework.IRdCall
import com.jetbrains.rd.framework.RdTaskResult
import com.jetbrains.rd.util.reactive.ISignal
import com.jetbrains.rdclient.daemon.HighlighterRegistrationHost
import com.jetbrains.rider.model.BlueprintClass
import com.jetbrains.rider.stacktrace.filters.RiderHeavyExceptionFilter
import com.jetbrains.rider.util.idea.application
import com.jetbrains.rider.util.idea.getLogger
import com.jetbrains.rider.util.idea.lifetime

class UnrealHeavyLogFilter(val project: Project, private val registrationHost: HighlighterRegistrationHost,
                           private val filter: IRdCall<List<BlueprintClass>, BooleanArray>, private val navigate: ISignal<BlueprintClass>) : Filter, FilterMixin {
    companion object {
        private val logger = getLogger<RiderHeavyExceptionFilter>()
    }

    private val urlFilter = UrlFilter()

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        return urlFilter.applyFilter(line, entireLength)
    }

    override fun shouldRunHeavy() = true

    override fun getUpdateMessage() = "Looking for valid Blueprint"

    override fun applyHeavyFilter(copiedFragment: Document, startOffset: Int, startLineNumber: Int, consumer: Consumer<in AdditionalHighlight>) {
        //heavy filters in tests can try to get access via Vfs to files outside of allowed roots
        if (application.isUnitTestMode) {
            return
        }

        val text = copiedFragment.charsSequence
        BlueprintParser.parse(text).let { candidates ->
            //todo change regex and send task immediately
            val request = candidates
                    .map {
                        BlueprintParser.split(it.value)
                    }
                    .toList()
            val task = filter.start(request)
            task.result.advise(project.lifetime) { rdTaskResult ->
                when (rdTaskResult) {
                    is RdTaskResult.Success -> {
                        rdTaskResult.value.let { isBlueprints ->
                            candidates.filterIndexed { index, _ ->
                                isBlueprints[index]
                            }.map { matchResult ->
                                val range = matchResult.range
                                val struct = BlueprintParser.split(matchResult.value)
                                val blueprintHyperLinkInfo = BlueprintHyperLinkInfo(navigate, struct)
                                val resultItems = Filter.ResultItem(range.first + startOffset, range.last + startOffset, blueprintHyperLinkInfo, true)
                                consumer.consume(AdditionalHighlight(arrayListOf(resultItems)))
                            }
                        }
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
