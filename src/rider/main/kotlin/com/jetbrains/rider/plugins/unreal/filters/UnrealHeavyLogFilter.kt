package com.jetbrains.rider.plugins.unreal.filters

import com.intellij.execution.filters.*
import com.intellij.execution.filters.FilterMixin.AdditionalHighlight
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.jetbrains.rd.framework.RdTaskResult
import com.jetbrains.rd.framework.impl.startAndAdviseSuccess
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.*
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.BlueprintClassHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.MethodReferenceHyperLinkInfo
import com.jetbrains.rider.plugins.unreal.filters.linkInfo.UnrealClassHyperLinkInfo
import com.jetbrains.rider.util.idea.getLogger

class UnrealHeavyLogFilter(val project: Project, private val model: RdRiderModel) : Filter, FilterMixin {
    companion object {
        private val logger = getLogger<UnrealHeavyLogFilter>()
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

        processMethodReferences(text, startOffset, consumer)

        processLinks(text, startOffset, consumer)
    }

    private fun processMethodReferences(text: CharSequence, startOffset: Int, consumer: Consumer<in AdditionalHighlight>) {
        LogParser.parseMethodReferences(text).forEach { match ->
            val range = match.range
            val (`class`, method) = match.value.split(MethodReference.separator)
            val methodReference = MethodReference(FString(`class`), FString(method))
            model.isMethodReference.startAndAdviseSuccess(methodReference) {
                if (it) {
                    logger.info("New method reference resolved = $methodReference, startOffset=${startOffset}, match.range.first=${match.range.first}")

                    run {
                        val first = startOffset + range.first
                        val last = startOffset + range.first + `class`.length
                        val linkInfo = UnrealClassHyperLinkInfo(model.navigateToBlueprintClass, BlueprintClass(FString(`class`)))
                        consumer.consume(AdditionalHighlight(listOf(Filter.ResultItem(first, last, linkInfo))))
                    }
                    run {
                        val linkInfo = MethodReferenceHyperLinkInfo(model.navigateToMethod, methodReference)
                        val first = startOffset + range.last - method.length + 1
                        val last = startOffset + range.last + 1
                        consumer.consume(AdditionalHighlight(listOf(Filter.ResultItem(first, last, linkInfo))))
                    }
                }
            }
        }
    }

    private fun processLinks(text: CharSequence, startOffset: Int, consumer: Consumer<in AdditionalHighlight>) {
        LogParser.parseLinkCandidates(text).let { candidates ->
            val request = candidates.toList()
            val task = model.filterLinkCandidates.start(Lifetime.Eternal, request.map { LinkRequest(FString(it.value)) })
            task.result.advise(project.lifetime) { rdTaskResult ->
                when (rdTaskResult) {
                    is RdTaskResult.Success -> {
                        rdTaskResult.value.let { response ->
                            val links = request.zip(response)
                                    .mapNotNull { (match, value) ->
                                        val matchStart = startOffset + match.range.first
                                        when (value) {
                                            is LinkResponseBlueprint -> {
                                                val substring = match.value.substring(value.range.first, value.range.last)
                                                logger.info("Blueprint link = $substring " +
                                                        "resolved from = ${match.value}, " +
                                                        "startOffset=${startOffset}, " +
                                                        "match.range.first=${match.range.first}")
                                                val hyperLinkInfo = BlueprintClassHyperLinkInfo(model.openBlueprint, BlueprintReference(value.fullPath))
                                                Filter.ResultItem(matchStart + value.range.first, matchStart + value.range.last, hyperLinkInfo, false)
                                            }
                                            is LinkResponseFilePath -> {
                                                val substring = match.value.substring(value.range.first, value.range.last)
                                                logger.info("File path = $substring " +
                                                        "resolved from = ${match.value}, " +
                                                        "startOffset=${startOffset}, " +
                                                        "match.range.first=${match.range.first}")
                                                val hyperLinkInfo = OpenUrlHyperlinkInfo(value.fullPath.data)
                                                Filter.ResultItem(matchStart + value.range.first, matchStart + value.range.last, hyperLinkInfo, false)
                                            }
                                            is LinkResponseUnresolved -> {
                                                null
                                            }
                                            else -> null
                                        }
                                    }
                            consumer.consume(AdditionalHighlight(links))
                        }
                    }
                    is RdTaskResult.Cancelled -> {
                        logger.debug("Request has been canceled: $request")
                    }
                    is RdTaskResult.Fault -> {
                        logger.error("Request has faulted: request=$request, result=$rdTaskResult")
                    }
                }
            }
        }
    }
}
