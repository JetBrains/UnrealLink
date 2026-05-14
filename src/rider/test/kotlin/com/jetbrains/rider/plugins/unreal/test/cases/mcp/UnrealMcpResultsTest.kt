package com.jetbrains.rider.plugins.unreal.test.cases.mcp

import com.jetbrains.rider.plugins.unreal.mcp.UnrealHealthResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealLogEntry
import com.jetbrains.rider.plugins.unreal.mcp.UnrealLogResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class UnrealMcpResultsTest {
    @Test
    fun `UnrealHealthResult serializes connected state`() {
        val result = UnrealHealthResult(
            connected = true,
            projectName = "MyGame",
            processId = 1234
        )
        val json = Json.encodeToString(result)
        assertTrue(json.contains("\"connected\":true"))
        assertTrue(json.contains("\"projectName\":\"MyGame\""))
    }

    @Test
    fun `UnrealLogResult serializes entries`() {
        val result = UnrealLogResult(entries = listOf(
            UnrealLogEntry(verbosity = "Log", category = "LogTemp", message = "Hello", timestampMs = 0L)
        ))
        val json = Json.encodeToString(result)
        assertTrue(json.contains("\"verbosity\":\"Log\""))
    }
}
