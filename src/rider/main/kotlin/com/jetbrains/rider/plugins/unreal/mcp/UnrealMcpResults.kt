package com.jetbrains.rider.plugins.unreal.mcp

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UnrealHealthResult(
    val connected: Boolean,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER) val projectName: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER) val processId: Int? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UnrealPlayResult(
    val state: String,  // "Idle" | "Play" | "Pause"
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER) val requested: String? = null,
)

@Serializable
data class UnrealLogEntry(
    val verbosity: String,
    val category: String,
    val message: String,
    val timestampMs: Long,
)

@Serializable
data class UnrealLogResult(
    val entries: List<UnrealLogEntry>,
    val total: Int = entries.size,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UnrealStatusResult(
    val connected: Boolean,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER) val projectName: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER) val processId: Int? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER) val playState: String? = null,  // omitted when disconnected
    val recentLogs: List<UnrealLogEntry> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UnrealScriptResult(
    val success: Boolean,
    val output: String,
    val result: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER) val error: String? = null,
)

@Serializable
data class UnrealBatchScriptResult(
    val results: List<UnrealScriptResult>,
    val lastSuccessfulIndex: Int,
)
