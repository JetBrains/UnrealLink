package com.jetbrains.rider.plugins.unreal.filters

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.FilterMixin
import com.intellij.execution.filters.FilterMixin.AdditionalHighlight
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.jetbrains.rd.framework.RdTaskResult
import com.jetbrains.rdclient.daemon.HighlighterRegistrationHost
import com.jetbrains.rider.model.FString
import com.jetbrains.rider.model.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.stacktrace.filters.RiderHeavyExceptionFilter
import com.jetbrains.rider.util.idea.application
import com.jetbrains.rider.util.idea.getLogger
import com.jetbrains.rider.util.idea.lifetime

class UnrealLogFilter(val project: Project, private val registrationHost: HighlighterRegistrationHost) : Filter, FilterMixin {
    companion object {
        private val logger = getLogger<RiderHeavyExceptionFilter>()
    }

    private val urlFilter = UrlFilter()

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        return urlFilter.applyFilter(line, entireLength)
    }

    override fun shouldRunHeavy() = true

    private val filterModel = project.solution.rdRiderModel.rdApplyFilter

    override fun getUpdateMessage() = "Looking for valid Blueprint"

    override fun applyHeavyFilter(copiedFragment: Document, startOffset: Int, startLineNumber: Int, consumer: Consumer<AdditionalHighlight>) {
        //heavy filters in tests can try to get access via Vfs to files outside of allowed roots
        if (application.isUnitTestMode) {
            return
        }

        val task = filterModel.start(FString(copiedFragment.text))//todo
        task.result.advise(project.lifetime) { rdTaskResult ->
            when (rdTaskResult) {
                is RdTaskResult.Success -> {
                    val result = arrayListOf<Filter.ResultItem>()
                    consumer.consume(AdditionalHighlight(result))
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
