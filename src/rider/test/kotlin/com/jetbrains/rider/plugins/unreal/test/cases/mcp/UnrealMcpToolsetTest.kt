package com.jetbrains.rider.plugins.unreal.test.cases.mcp

import com.jetbrains.rider.plugins.unreal.mcp.LogFilter
import com.jetbrains.rider.plugins.unreal.mcp.UnrealMcpToolset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UnrealMcpToolsetTest {
    @Test
    fun `toolset is not experimental`() {
        val toolset = UnrealMcpToolset()
        assertFalse(toolset.isExperimental())
    }

    @Test
    fun `LogFilter defaults to count 200`() {
        val filter = LogFilter()
        assertEquals(200, filter.count)
    }
}
