package com.jetbrains.rider.plugins.unreal.test.cases.mcp

import com.intellij.mcpserver.annotations.McpTool
import com.jetbrains.rider.plugins.unreal.mcp.LogFilter
import com.jetbrains.rider.plugins.unreal.mcp.UnrealMcpToolset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun `ue_find_blueprint_usages method is declared with McpTool annotation`() {
        val method = UnrealMcpToolset::class.java.declaredMethods
            .firstOrNull { it.name == "ue_find_blueprint_usages" }
        assertNotNull(method, "ue_find_blueprint_usages must exist")
        assertTrue(
            method!!.isAnnotationPresent(McpTool::class.java),
            "ue_find_blueprint_usages must have @McpTool"
        )
    }
}
