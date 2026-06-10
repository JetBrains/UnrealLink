package com.jetbrains.rider.plugins.unreal.test.cases.mcp

import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.jetbrains.rider.plugins.unreal.mcp.LogFilter
import com.jetbrains.rider.plugins.unreal.mcp.UnrealBatchScriptResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealExportBlueprintNodesResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealImportBlueprintNodesResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealMcpToolset
import com.jetbrains.rider.plugins.unreal.mcp.UnrealPlayResult
import com.jetbrains.rider.plugins.unreal.mcp.UnrealStatusResult
import org.junit.jupiter.api.Test
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UnrealMcpToolsetTest {
    private val toolFunctions: List<KFunction<*>> =
        UnrealMcpToolset::class.declaredMemberFunctions
            .filter { it.findAnnotation<McpTool>() != null }

    private fun tool(name: String): KFunction<*> =
        toolFunctions.firstOrNull { it.name == name }
            ?: error("Tool '$name' is not declared on UnrealMcpToolset")

    private fun valueParams(name: String) =
        tool(name).parameters.filter { it.kind == kotlin.reflect.KParameter.Kind.VALUE }

    @Test
    fun `toolset is not experimental`() {
        assertFalse(UnrealMcpToolset().isExperimental())
    }

    @Test
    fun `LogFilter defaults to count 200`() {
        assertEquals(200, LogFilter().count)
    }

    @Test
    fun `exposes exactly the expected MCP tools`() {
        val expected = setOf("ue_health", "ue_play", "ue_get_logs", "ue_execute_python", "ue_status", "ue_export_blueprint_nodes", "ue_import_blueprint_nodes")
        assertEquals(expected, toolFunctions.map { it.name }.toSet())
    }

    @Test
    fun `removed tools are no longer declared`() {
        val names = UnrealMcpToolset::class.declaredMemberFunctions.map { it.name }.toSet()
        val removed = listOf(
            "ue_trigger_build",
            "ue_open_blueprint",
            "ue_find_blueprint_usages",
            "ue_play_control",
            "ue_get_play_state",
            "ue_set_play_mode",
            "ue_execute_python_batch",
        )
        for (name in removed) {
            assertFalse(name in names, "$name must not exist")
        }
    }

    @Test
    fun `every MCP tool is suspend and has an McpDescription`() {
        assertTrue(toolFunctions.isNotEmpty())
        for (fn in toolFunctions) {
            assertTrue(fn.isSuspend, "${fn.name} must be suspend")
            assertNotNull(fn.findAnnotation<McpDescription>(), "${fn.name} must carry @McpDescription")
        }
    }

    @Test
    fun `every MCP tool name has the ue_ prefix`() {
        for (fn in toolFunctions) {
            assertTrue(fn.name.startsWith("ue_"), "${fn.name} must start with ue_")
        }
    }

    @Test
    fun `ue_health takes no arguments and returns a health result`() {
        assertEquals(0, valueParams("ue_health").size)
        assertEquals(
            "com.jetbrains.rider.plugins.unreal.mcp.UnrealHealthResult",
            tool("ue_health").returnType.toString(),
        )
    }

    @Test
    fun `ue_play accepts action plus optional play params and returns UnrealPlayResult`() {
        val params = valueParams("ue_play").associateBy { it.name }
        assertEquals(
            setOf(
                "action", "mode", "players", "netMode",
                "dedicatedServer", "spawnAtPlayerStart", "compileBeforeRun",
                "runUnderOneProcess",
            ),
            params.keys,
        )

        // action is required (no default); the rest are optional with defaults.
        assertFalse(params["action"]!!.isOptional, "action must be required")
        assertTrue(params["mode"]!!.isOptional, "mode must have a default")
        assertTrue(params["players"]!!.isOptional, "players must have a default")
        assertTrue(params["netMode"]!!.isOptional, "netMode must have a default")
        assertTrue(params["dedicatedServer"]!!.isOptional, "dedicatedServer must have a default")
        assertTrue(params["spawnAtPlayerStart"]!!.isOptional, "spawnAtPlayerStart must have a default")
        assertTrue(params["compileBeforeRun"]!!.isOptional, "compileBeforeRun must have a default")
        assertTrue(params["runUnderOneProcess"]!!.isOptional, "runUnderOneProcess must have a default")

        // None of the play params are nullable.
        for ((name, p) in params) {
            assertFalse(p.type.isMarkedNullable, "ue_play.$name must not be nullable")
        }

        // `mode` and `netMode` are Strings (accept int-as-string or alias).
        assertEquals("kotlin.String", params["mode"]!!.type.toString())
        assertEquals("kotlin.String", params["netMode"]!!.type.toString())

        assertEquals(UnrealPlayResult::class, tool("ue_play").returnType.classifier)
    }

    @Test
    fun `ue_get_logs accepts the documented filter and follow params`() {
        val params = valueParams("ue_get_logs").associateBy { it.name }
        assertEquals(
            setOf("category", "minVerbosity", "count", "sinceTimestampMs", "pattern", "follow", "followTimeoutMs"),
            params.keys,
        )

        // String-ish optional filters are nullable.
        assertTrue(params["category"]!!.type.isMarkedNullable)
        assertTrue(params["minVerbosity"]!!.type.isMarkedNullable)
        assertTrue(params["sinceTimestampMs"]!!.type.isMarkedNullable)
        assertTrue(params["pattern"]!!.type.isMarkedNullable)
        // Primitives with defaults are not nullable.
        assertFalse(params["count"]!!.type.isMarkedNullable)
        assertFalse(params["follow"]!!.type.isMarkedNullable)
        assertFalse(params["followTimeoutMs"]!!.type.isMarkedNullable)
        // Every parameter is optional — callers can use the tool with no args.
        params.values.forEach { assertTrue(it.isOptional, "${it.name} must be optional") }
    }

    @Test
    fun `ue_status returns combined health + play + log slice and is fully optional`() {
        val params = valueParams("ue_status").associateBy { it.name }
        assertEquals(setOf("count", "category", "minVerbosity", "pattern", "sinceTimestampMs"), params.keys)
        params.values.forEach { assertTrue(it.isOptional, "${it.name} must be optional") }
        // Filter params have the same nullability shape as ue_get_logs.
        assertTrue(params["category"]!!.type.isMarkedNullable)
        assertTrue(params["minVerbosity"]!!.type.isMarkedNullable)
        assertTrue(params["pattern"]!!.type.isMarkedNullable)
        assertTrue(params["sinceTimestampMs"]!!.type.isMarkedNullable)
        assertFalse(params["count"]!!.type.isMarkedNullable)
        assertEquals(UnrealStatusResult::class, tool("ue_status").returnType.classifier)
    }

    @Test
    fun `ue_execute_python is fully optional and always returns a batch result`() {
        val params = valueParams("ue_execute_python").associateBy { it.name }
        assertEquals(setOf("script", "scripts", "startFrom", "isolated"), params.keys)

        // Every parameter is optional (so the toolset can decide based on which is set).
        params.values.forEach { assertTrue(it.isOptional, "${it.name} must be optional") }

        // `script` and `scripts` are nullable; the primitives are not.
        assertTrue(params["script"]!!.type.isMarkedNullable, "script must be nullable")
        assertTrue(params["scripts"]!!.type.isMarkedNullable, "scripts must be nullable")
        assertFalse(params["startFrom"]!!.type.isMarkedNullable)
        assertFalse(params["isolated"]!!.type.isMarkedNullable)

        // The single-script return is wrapped as a 1-item batch, so the shape is always UnrealBatchScriptResult.
        assertEquals(UnrealBatchScriptResult::class, tool("ue_execute_python").returnType.classifier)
    }

    @Test
    fun `ue_export_blueprint_nodes has the expected params and return type`() {
        val params = valueParams("ue_export_blueprint_nodes").associateBy { it.name }
        assertEquals(setOf("blueprintPath", "graphName", "nodeNames"), params.keys)
        assertFalse(params["blueprintPath"]!!.isOptional, "blueprintPath must be required")
        assertFalse(params["graphName"]!!.isOptional, "graphName must be required")
        assertTrue(params["nodeNames"]!!.isOptional, "nodeNames must be optional")
        assertTrue(params["nodeNames"]!!.type.isMarkedNullable, "nodeNames must be nullable")
        assertEquals(UnrealExportBlueprintNodesResult::class, tool("ue_export_blueprint_nodes").returnType.classifier)
    }

    @Test
    fun `ue_import_blueprint_nodes has the expected params and return type`() {
        val params = valueParams("ue_import_blueprint_nodes").associateBy { it.name }
        assertEquals(setOf("blueprintPath", "graphName", "clipboardText", "offsetX", "offsetY"), params.keys)
        assertFalse(params["blueprintPath"]!!.isOptional, "blueprintPath must be required")
        assertFalse(params["graphName"]!!.isOptional, "graphName must be required")
        assertFalse(params["clipboardText"]!!.isOptional, "clipboardText must be required")
        assertTrue(params["offsetX"]!!.isOptional, "offsetX must have a default")
        assertTrue(params["offsetY"]!!.isOptional, "offsetY must have a default")
        assertFalse(params["offsetX"]!!.type.isMarkedNullable, "offsetX must not be nullable")
        assertFalse(params["offsetY"]!!.type.isMarkedNullable, "offsetY must not be nullable")
        assertEquals(UnrealImportBlueprintNodesResult::class, tool("ue_import_blueprint_nodes").returnType.classifier)
    }
}
