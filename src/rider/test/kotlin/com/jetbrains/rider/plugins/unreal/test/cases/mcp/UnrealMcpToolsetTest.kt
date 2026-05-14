package com.jetbrains.rider.plugins.unreal.test.cases.mcp

import com.jetbrains.rider.plugins.unreal.mcp.UnrealMcpToolset
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class UnrealMcpToolsetTest {
    @Test
    fun `toolset is not experimental`() {
        val toolset = UnrealMcpToolset()
        assertFalse(toolset.isExperimental())
    }
}
