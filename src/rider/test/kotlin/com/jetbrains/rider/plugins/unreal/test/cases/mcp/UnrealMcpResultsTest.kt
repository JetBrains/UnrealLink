package com.jetbrains.rider.plugins.unreal.test.cases.mcp

import com.jetbrains.rider.plugins.unreal.mcp.UnrealBatchScriptResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealHealthResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealLogEntry
import com.jetbrains.rider.plugins.unreal.mcp.UnrealLogResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealPlayResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealScriptResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealStatusResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnrealMcpResultsTest {
    @Test
    fun `UnrealHealthResult serializes connected state`() {
        val json = Json.encodeToString(
            UnrealHealthResult(connected = true, projectName = "MyGame", processId = 1234)
        )
        assertTrue(json.contains("\"connected\":true"))
        assertTrue(json.contains("\"projectName\":\"MyGame\""))
        assertTrue(json.contains("\"processId\":1234"))
    }

    @Test
    fun `UnrealHealthResult omits projectName and processId when disconnected`() {
        val json = Json.encodeToString(UnrealHealthResult(connected = false))
        assertEquals("""{"connected":false}""", json)
    }

    @Test
    fun `UnrealLogResult serializes entries`() {
        val json = Json.encodeToString(
            UnrealLogResult(
                entries = listOf(
                    UnrealLogEntry(verbosity = "Log", category = "LogTemp", message = "Hello", timestampMs = 0L)
                )
            )
        )
        assertTrue(json.contains("\"verbosity\":\"Log\""))
        assertTrue(json.contains("\"category\":\"LogTemp\""))
    }

    @Test
    fun `UnrealLogResult total defaults to entries size`() {
        val empty = UnrealLogResult(entries = emptyList())
        assertEquals(0, empty.total)
        val three = UnrealLogResult(
            entries = List(3) { i ->
                UnrealLogEntry(verbosity = "Log", category = "C", message = "m$i", timestampMs = i.toLong())
            }
        )
        assertEquals(3, three.total)
    }

    @Test
    fun `UnrealPlayResult serializes state-only when requested is null`() {
        val json = Json.encodeToString(UnrealPlayResult(state = "Idle"))
        assertEquals("""{"state":"Idle"}""", json)
        assertFalse(json.contains("requested"))
    }

    @Test
    fun `UnrealPlayResult includes requested when set`() {
        val json = Json.encodeToString(UnrealPlayResult(state = "Play", requested = "play"))
        assertTrue(json.contains("\"state\":\"Play\""))
        assertTrue(json.contains("\"requested\":\"play\""))
    }

    @Test
    fun `UnrealScriptResult omits error when null`() {
        val json = Json.encodeToString(
            UnrealScriptResult(success = true, output = "ok", result = "42", error = null)
        )
        assertTrue(json.contains("\"success\":true"))
        assertTrue(json.contains("\"output\":\"ok\""))
        assertTrue(json.contains("\"result\":\"42\""))
        assertFalse(json.contains("error"))
    }

    @Test
    fun `UnrealScriptResult includes error when set`() {
        val json = Json.encodeToString(
            UnrealScriptResult(success = false, output = "", result = "", error = "boom")
        )
        assertTrue(json.contains("\"success\":false"))
        assertTrue(json.contains("\"error\":\"boom\""))
    }

    @Test
    fun `UnrealBatchScriptResult shape supports single-script wrap`() {
        val batch = UnrealBatchScriptResult(
            results = listOf(UnrealScriptResult(success = true, output = "ok", result = "", error = null)),
            lastSuccessfulIndex = 0,
        )
        assertEquals(1, batch.results.size)
        assertEquals(0, batch.lastSuccessfulIndex)
        val json = Json.encodeToString(batch)
        assertTrue(json.contains("\"lastSuccessfulIndex\":0"))
        assertTrue(json.contains("\"results\":["))
    }

    @Test
    fun `UnrealStatusResult disconnected omits projectName, processId, playState`() {
        val json = Json.encodeToString(UnrealStatusResult(connected = false))
        // recentLogs = emptyList() is the declared default, so kotlinx-serialization omits it too.
        assertEquals("""{"connected":false}""", json)
    }

    @Test
    fun `UnrealStatusResult connected includes playState and recentLogs`() {
        val status = UnrealStatusResult(
            connected = true,
            projectName = "MyGame",
            processId = 4242,
            playState = "Play",
            recentLogs = listOf(
                UnrealLogEntry(verbosity = "Log", category = "LogTemp", message = "tick", timestampMs = 1L)
            ),
        )
        val json = Json.encodeToString(status)
        assertTrue(json.contains("\"connected\":true"))
        assertTrue(json.contains("\"projectName\":\"MyGame\""))
        assertTrue(json.contains("\"processId\":4242"))
        assertTrue(json.contains("\"playState\":\"Play\""))
        assertTrue(json.contains("\"category\":\"LogTemp\""))
    }

    @Test
    fun `UnrealBatchScriptResult reports negative lastSuccessfulIndex when first script fails`() {
        val batch = UnrealBatchScriptResult(
            results = listOf(UnrealScriptResult(success = false, output = "", result = "", error = "x")),
            lastSuccessfulIndex = -1,
        )
        assertEquals(-1, batch.lastSuccessfulIndex)
    }
}
