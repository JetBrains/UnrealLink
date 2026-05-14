package com.jetbrains.rider.plugins.unreal.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.actions.PlayStateActionStateService
import com.jetbrains.rider.plugins.unreal.model.BlueprintReference
import com.jetbrains.rider.plugins.unreal.model.FString
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ILinkResponse
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.LinkRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.LinkResponseBlueprint
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.LinkResponseFilePath
import kotlinx.coroutines.currentCoroutineContext

class UnrealMcpToolset : McpToolset {

    override fun isExperimental(): Boolean = false

    private suspend fun requireConnected(): UnrealHost {
        val project = currentCoroutineContext().project
        val host = UnrealHost.getInstance(project)
        if (!host.isConnectedToUnrealEditor) {
            mcpFail("Unreal Editor is not connected. Open a .uproject in Rider and ensure RiderLink is installed.")
        }
        return host
    }

    @McpTool
    @McpDescription("""
        |Check whether Unreal Editor is connected to Rider via RiderLink.
        |Returns connection status and, if connected, the project name and editor process ID.
        |Call this before any other ue_* tool to confirm the editor is reachable.
    """)
    suspend fun ue_health(): UnrealHealthResult {
        currentCoroutineContext().reportToolActivity("Checking Unreal Editor connection")
        val project = currentCoroutineContext().project
        val host = UnrealHost.getInstance(project)
        val info = host.connectionInfo
        val processAlive = info?.processId?.let { pid ->
            ProcessHandle.of(pid.toLong()).map { it.isAlive }.orElse(false)
        } ?: false
        val connected = host.isConnectedToUnrealEditor && processAlive
        return UnrealHealthResult(
            connected = connected,
            projectName = if (connected) info?.projectName else null,
            processId = if (connected) info?.processId else null,
        )
    }

    @McpTool
    @McpDescription("""
        |Get the current PIE (Play In Editor) state.
        |Returns one of: "Idle" (not playing), "Play" (playing), "Pause" (paused).
        |Use before ue_play_control to check whether play/pause/stop is valid.
    """)
    suspend fun ue_get_play_state(): UnrealPlayStateResult {
        currentCoroutineContext().reportToolActivity("Getting play state")
        val host = requireConnected()
        return UnrealPlayStateResult(state = host.playState.name)
    }

    @McpTool
    @McpDescription("""
        |Control PIE (Play In Editor) in Unreal Editor.
        |action must be one of: "play", "pause", "resume", "stop", "frame_skip".
        |"play" starts PIE in the currently configured mode.
        |"pause" freezes a running session; "resume" unfreezes it.
        |"stop" ends the PIE session.
        |"frame_skip" advances one frame while paused.
        |Returns the action that was requested.
    """)
    suspend fun ue_play_control(
        @McpDescription("One of: play | pause | resume | stop | frame_skip")
        action: String,
    ): String {
        currentCoroutineContext().reportToolActivity("Play control: $action")
        val host = requireConnected()
        val model = host.model
        val stateService = PlayStateActionStateService.getInstance(currentCoroutineContext().project)
        val requestId = stateService.nextRequestID()
        when (action.lowercase()) {
            "play"       -> model.requestPlayFromRider.fire(requestId)
            "pause"      -> model.requestPauseFromRider.fire(requestId)
            "resume"     -> model.requestResumeFromRider.fire(requestId)
            "stop"       -> model.requestStopFromRider.fire(requestId)
            "frame_skip" -> model.requestFrameSkipFromRider.fire(requestId)
            else         -> mcpFail("Unknown action '$action'. Use: play | pause | resume | stop | frame_skip")
        }
        return "Requested: $action (requestId=$requestId)"
    }

    @McpTool
    @McpDescription("""
        |Set the PIE play mode before calling ue_play_control("play").
        |mode: 0=Viewport(default), 1=MobilePreview, 2=EditorFloating, 3=VR, 4=StandaloneProcess, 5=Simulate.
        |players: number of player windows (1-4). Default 1.
        |dedicatedServer: if true, launches a dedicated server alongside clients. Default false.
        |spawnAtPlayerStart: if true, spawns player at PlayerStart actor. Default false.
    """)
    suspend fun ue_set_play_mode(
        @McpDescription("Play mode index: 0=Viewport 1=MobilePreview 2=FloatingWindow 3=VR 4=Standalone 5=Simulate")
        mode: Int = 0,
        @McpDescription("Number of player windows (1-4)") players: Int = 1,
        @McpDescription("Launch a dedicated server alongside clients") dedicatedServer: Boolean = false,
        @McpDescription("Spawn player at PlayerStart actor") spawnAtPlayerStart: Boolean = false,
    ): String {
        currentCoroutineContext().reportToolActivity("Setting play mode $mode")
        val host = requireConnected()
        if (mode !in 0..5) mcpFail("mode must be 0-5")
        if (players !in 1..4) mcpFail("players must be 1-4")
        val encoded = mode or ((players - 1) and 3) or
                      (if (spawnAtPlayerStart) 4 else 0) or
                      (if (dedicatedServer) 8 else 0)
        host.model.playModeFromRider.fire(encoded)
        return "Play mode set (encoded=$encoded)"
    }

    @McpTool
    @McpDescription("""
        |Trigger a Hot Reload or Live Coding compile in Unreal Editor.
        |Compiles changed C++ modules without restarting the editor.
        |Returns immediately; monitor the editor for compilation progress.
        |Fails if Unreal Editor is not connected or Hot Reload is not available.
    """)
    suspend fun ue_trigger_build(): String {
        currentCoroutineContext().reportToolActivity("Triggering Hot Reload / Live Coding")
        val host = requireConnected()
        if (!host.isHotReloadAvailable) {
            mcpFail("Hot Reload / Live Coding is not available. Ensure the project has C++ modules.")
        }
        host.model.triggerHotReload.fire()
        return "Build triggered"
    }

    @McpTool
    @McpDescription("""
        |Query recent log entries streamed from Unreal Editor.
        |Requires Unreal Editor to be connected; the buffer accumulates entries while connected.
        |category: filter by log category name (e.g. "LogTemp", "LogBlueprintUserMessages"). Omit for all.
        |minVerbosity: minimum severity to include: "Fatal" | "Error" | "Warning" | "Display" | "Log" | "Verbose" | "VeryVerbose". Omit for all.
        |count: maximum entries to return (most recent). Default 200, max 1000.
        |sinceTimestampMs: only return entries with timestamp >= this value (epoch milliseconds). Omit for no time filter.
        |Returns entries ordered oldest-first.
    """)
    suspend fun ue_get_logs(
        @McpDescription("Log category name to filter by, e.g. LogTemp. Omit for all categories.")
        category: String? = null,
        @McpDescription("Minimum verbosity: Fatal | Error | Warning | Display | Log | Verbose | VeryVerbose")
        minVerbosity: String? = null,
        @McpDescription("Maximum number of entries to return (most recent). Default 200.")
        count: Int = 200,
        @McpDescription("Epoch milliseconds — return only entries at or after this timestamp.")
        sinceTimestampMs: Long? = null,
    ): UnrealLogResult {
        currentCoroutineContext().reportToolActivity("Querying Unreal logs")
        val project = currentCoroutineContext().project
        if (count !in 1..1000) mcpFail("count must be between 1 and 1000")
        val entries = UnrealLogBuffer.getInstance(project).query(
            LogFilter(
                category = category,
                minVerbosity = minVerbosity,
                count = count,
                sinceTimestampMs = sinceTimestampMs,
            )
        )
        return UnrealLogResult(entries = entries)
    }

    @McpTool
    @McpDescription("""
        |Open a Blueprint asset in Unreal Editor's visual graph editor.
        |path must be a valid Unreal asset path, e.g. "/Game/Blueprints/BP_MyActor.BP_MyActor".
        |The editor window is brought to focus after opening.
    """)
    suspend fun ue_open_blueprint(
        @McpDescription("Unreal asset path to the Blueprint, e.g. /Game/Blueprints/BP_MyActor.BP_MyActor")
        path: String,
    ): String {
        currentCoroutineContext().reportToolActivity("Opening Blueprint: $path")
        val host = requireConnected()
        host.model.openBlueprint.fire(
            BlueprintReference(
                pathName = FString(path),
                guid = FString(""),
            )
        )
        return "Blueprint open requested: $path"
    }

    @McpTool
    @McpDescription("""
        |Find Blueprint assets and file paths that reference a given C++ symbol or Blueprint path string.
        |Uses Rider's ReSharper backend Blueprint indexing (powered by RiderLink) to resolve links.
        |symbol: the string to look up, e.g. a C++ class name, method name, or Blueprint asset path.
        |Returns a list of resolved usages with full asset paths and text ranges.
        |Empty result means no Blueprint references were found — the symbol may be C++-only.
        |Requires the editor to be connected because the backend uses IsBlueprintPathName from RiderLink.
    """)
    suspend fun ue_find_blueprint_usages(
        @McpDescription("C++ symbol name or Blueprint asset path string to look up.")
        symbol: String,
    ): UnrealBlueprintUsagesResult {
        currentCoroutineContext().reportToolActivity("Finding Blueprint usages of: $symbol")
        val host = requireConnected()
        val responses = host.model.filterLinkCandidates.startSuspending(
            listOf(LinkRequest(data = FString(symbol)))
        )
        val usages = responses.mapNotNull { response ->
            when (response) {
                is LinkResponseBlueprint -> UnrealBlueprintUsage(
                    fullPath = response.fullPath.data,
                    rangeStart = response.range.first,
                    rangeEnd = response.range.last,
                )
                is LinkResponseFilePath -> UnrealBlueprintUsage(
                    fullPath = response.fullPath.data,
                    rangeStart = response.range.first,
                    rangeEnd = response.range.last,
                )
                else -> null
            }
        }
        return UnrealBlueprintUsagesResult(usages = usages)
    }

    @McpTool
    @McpDescription("""
        |Execute a Python script inside Unreal Editor using the built-in Python plugin.
        |The script runs on the editor's game thread with full access to Unreal Python API.
        |script: valid Python code as a string. Multi-line scripts are supported.
        |isolated: if true, the script runs in EvaluateStatement mode (returns expression value). Default false.
        |Returns stdout output, expression result, and any error messages.
        |Output is capped at 10,000 characters to avoid context overflow.
        |Reference: https://dev.epicgames.com/documentation/en-us/unreal-engine/python-api/
    """)
    suspend fun ue_execute_python(
        @McpDescription("Python code to execute inside Unreal Editor.")
        script: String,
        @McpDescription("Run in isolated scope (EvaluateStatement mode, returns expression value). Default false.")
        isolated: Boolean = false,
    ): UnrealScriptResult {
        currentCoroutineContext().reportToolActivity("Executing Python in UE")
        val host = requireConnected()
        val result = host.model.executeScript.startSuspending(
            com.jetbrains.rider.plugins.unreal.model.ScriptRequest(
                script = FString(script),
                isolated = isolated,
            )
        )
        return UnrealScriptResult(
            success = result.success,
            output = result.output.data,
            result = result.result.data,
            error = result.error.data.takeIf { it.isNotEmpty() },
        )
    }

    @McpTool
    @McpDescription("""
        |Execute multiple Python scripts sequentially in Unreal Editor with resume-on-failure support.
        |scripts: list of Python code strings to execute in order.
        |startFrom: resume from this 0-based index (use lastSuccessfulIndex + 1 from a previous failed call).
        |Stops on the first failure and returns lastSuccessfulIndex.
        |To resume after a partial failure: call again with startFrom = lastSuccessfulIndex + 1.
    """)
    suspend fun ue_execute_python_batch(
        @McpDescription("List of Python scripts to execute sequentially.")
        scripts: List<String>,
        @McpDescription("0-based index to resume from (default 0 = start from beginning).")
        startFrom: Int = 0,
    ): UnrealBatchScriptResult {
        currentCoroutineContext().reportToolActivity("Executing Python batch in UE")
        val host = requireConnected()
        val result = host.model.executeBatchScripts.startSuspending(
            com.jetbrains.rider.plugins.unreal.model.BatchScriptRequest(
                scripts = scripts.map { FString(it) },
                startFrom = startFrom,
            )
        )
        return UnrealBatchScriptResult(
            results = result.results.map { r ->
                UnrealScriptResult(
                    success = r.success,
                    output = r.output.data,
                    result = r.result.data,
                    error = r.error.data.takeIf { it.isNotEmpty() },
                )
            },
            lastSuccessfulIndex = result.lastSuccessfulIndex,
        )
    }
}
