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
}
