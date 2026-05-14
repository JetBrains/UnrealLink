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

@Serializable
data class UnrealPlayStateResult(
    val state: String,  // "Idle" | "Play" | "Pause"
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

@Serializable
data class UnrealBlueprintUsage(
    val fullPath: String,
    val rangeStart: Int,
    val rangeEnd: Int,
)

@Serializable
data class UnrealBlueprintUsagesResult(
    val usages: List<UnrealBlueprintUsage>,
)
