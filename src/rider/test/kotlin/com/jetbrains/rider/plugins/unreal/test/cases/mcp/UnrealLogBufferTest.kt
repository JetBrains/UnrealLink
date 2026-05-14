package com.jetbrains.rider.plugins.unreal.test.cases.mcp

import com.jetbrains.rider.plugins.unreal.mcp.LogFilter
import com.jetbrains.rider.plugins.unreal.mcp.UnrealLogBuffer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnrealLogBufferTest {

    @Test
    fun `empty buffer returns empty list`() {
        val buffer = UnrealLogBuffer.createForTest()
        assertEquals(emptyList(), buffer.query(LogFilter()))
    }

    @Test
    fun `category filter matches exactly`() {
        val buffer = UnrealLogBuffer.createForTest()
        buffer.addForTest("Log", "LogTemp", "hello")
        buffer.addForTest("Log", "LogAI", "world")
        val result = buffer.query(LogFilter(category = "LogTemp"))
        assertEquals(1, result.size)
        assertEquals("hello", result[0].message)
    }

    @Test
    fun `verbosity filter excludes lower-priority entries`() {
        val buffer = UnrealLogBuffer.createForTest()
        buffer.addForTest("Log", "LogTemp", "verbose-msg")
        buffer.addForTest("Warning", "LogTemp", "warn-msg")
        buffer.addForTest("Error", "LogTemp", "err-msg")
        val result = buffer.query(LogFilter(minVerbosity = "Warning"))
        assertEquals(2, result.size)
        assertTrue(result.all { it.verbosity in setOf("Warning", "Error") })
    }

    @Test
    fun `count limits returned entries to most recent`() {
        val buffer = UnrealLogBuffer.createForTest()
        repeat(10) { buffer.addForTest("Log", "LogTemp", "msg$it") }
        val result = buffer.query(LogFilter(count = 3))
        assertEquals(3, result.size)
        assertEquals("msg9", result[2].message)
    }

    @Test
    fun `clear removes all entries`() {
        val buffer = UnrealLogBuffer.createForTest()
        buffer.addForTest("Log", "LogTemp", "msg")
        buffer.clear()
        assertEquals(emptyList(), buffer.query(LogFilter()))
    }

    @Test
    fun `sinceTimestampMs filter excludes older entries`() {
        val buffer = UnrealLogBuffer.createForTest()
        // Capture both timestamps before/after a marker so the marker sits strictly between them
        buffer.addForTest("Log", "LogTemp", "before")
        val marker = System.currentTimeMillis()
        Thread.sleep(2)  // ensure subsequent timestamp is strictly greater
        buffer.addForTest("Log", "LogTemp", "after")

        val result = buffer.query(LogFilter(sinceTimestampMs = marker + 1))
        assertEquals(1, result.size)
        assertEquals("after", result[0].message)
    }

    @Test
    fun `buffer evicts oldest entries when MAX_ENTRIES exceeded`() {
        val buffer = UnrealLogBuffer.createForTest()
        val overflow = 5
        repeat(UnrealLogBuffer.MAX_ENTRIES + overflow) {
            buffer.addForTest("Log", "LogTemp", "msg$it")
        }
        val result = buffer.query(LogFilter(count = UnrealLogBuffer.MAX_ENTRIES + overflow))
        assertEquals(UnrealLogBuffer.MAX_ENTRIES, result.size)
        // First `overflow` entries should have been evicted; result[0] is the oldest remaining
        assertEquals("msg$overflow", result[0].message)
        assertEquals("msg${UnrealLogBuffer.MAX_ENTRIES + overflow - 1}", result.last().message)
    }
}
