package com.jetbrains.rider.plugins.unreal.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.RdRiderModel

data class LogFilter(
    val category: String? = null,
    val minVerbosity: String? = null,  // "Log" | "Warning" | "Error" | "Fatal"
    val count: Int = 200,
    val sinceTimestampMs: Long? = null,
)

/** Verbosity ordering matching UE's ELogVerbosity — lower ordinal = more severe. */
private val VERBOSITY_ORDER = mapOf(
    "Fatal" to 0, "Error" to 1, "Warning" to 2,
    "Display" to 3, "Log" to 4, "Verbose" to 5, "VeryVerbose" to 6,
)

@Service(Service.Level.PROJECT)
class UnrealLogBuffer(private val project: Project? = null) {  // null only in tests

    companion object {
        private const val MAX_ENTRIES = 32 * 1024

        fun getInstance(project: Project): UnrealLogBuffer = project.service()

        /** For unit tests only — creates a disconnected buffer without project DI. */
        fun createForTest(): UnrealLogBuffer = UnrealLogBuffer(null)
    }

    private val buffer = ArrayDeque<UnrealLogEntry>(MAX_ENTRIES + 1)

    // Called by UnrealHost.ProtocolListener to wire subscription for the connection lifetime
    fun attach(lifetime: Lifetime, model: RdRiderModel) {
        clear()
        model.unrealLog.advise(lifetime) { event ->
            val entry = UnrealLogEntry(
                verbosity = event.info.type.name,
                category = event.info.category.data,
                message = event.text.data,
                timestampMs = event.info.time?.time ?: System.currentTimeMillis(),
            )
            synchronized(buffer) {
                buffer.addLast(entry)
                if (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            }
        }
        lifetime.onTermination { clear() }
    }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
    }

    fun query(filter: LogFilter): List<UnrealLogEntry> {
        val minOrder = filter.minVerbosity?.let { VERBOSITY_ORDER[it] } ?: Int.MAX_VALUE
        val sinceMs = filter.sinceTimestampMs

        val snapshot = synchronized(buffer) { buffer.toList() }
        return snapshot
            .filter { entry ->
                (filter.category == null || entry.category == filter.category) &&
                (filter.minVerbosity == null || (VERBOSITY_ORDER[entry.verbosity] ?: Int.MAX_VALUE) <= minOrder) &&
                (sinceMs == null || entry.timestampMs >= sinceMs)
            }
            .takeLast(filter.count)
    }

    // Test helpers — only called from UnrealLogBufferTest
    fun addForTest(verbosity: String, category: String, message: String) {
        val entry = UnrealLogEntry(verbosity, category, message, System.currentTimeMillis())
        synchronized(buffer) {
            buffer.addLast(entry)
            if (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        }
    }
}
