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
import com.jetbrains.rider.plugins.unreal.model.BatchScriptRequest
import com.jetbrains.rider.plugins.unreal.model.FString
import com.jetbrains.rider.plugins.unreal.model.PlayNetMode
import com.jetbrains.rider.plugins.unreal.model.PlaySettings
import com.jetbrains.rider.plugins.unreal.model.ScriptRequest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

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
        |Query or control PIE (Play In Editor).
        |action: "state" | "play" | "pause" | "resume" | "stop" | "frame_skip".
        |  "state" reads the current state without firing any signal.
        |  "play" applies all the optional parameters below, then starts PIE. UE persists these values to
        |  the editor's PIE settings (SaveConfig) so subsequent plays inherit them unless overridden.
        |  "pause"/"resume"/"stop"/"frame_skip" act on a running session; mode parameters are ignored.
        |Mode parameter values (mapping matches UE's EPlayModeType):
        |  0 = Viewport (selected viewport, default)
        |  1 = MobilePreview
        |  2 = EditorFloating (new editor window)
        |  3 = VR
        |  4 = Standalone (new process)
        |  5 = Simulate
        |Mode-string aliases for `mode` (case-insensitive): "viewport", "mobile" / "mobilepreview",
        |"floating" / "editorfloating" / "new-window", "vr", "standalone" / "newprocess", "simulate".
        |Net mode aliases for `netMode` (case-insensitive):
        |  "standalone" (default) — no networking, each PIE world independent.
        |  "listen" / "listenserver" — first client window also hosts the listen server.
        |  "client" — all PIE worlds are clients; pair with `dedicatedServer=true` to spawn a server too.
        |`runUnderOneProcess` controls whether all client/server PIE instances share the editor process
        |(default true) or are launched as separate processes.
        |Returns the state observed before the requested action (state transitions are async) and which action
        |was requested. For action="state", `requested` is omitted.
    """)
    suspend fun ue_play(
        @McpDescription("One of: state | play | pause | resume | stop | frame_skip")
        action: String,
        @McpDescription("Play mode (only for action=play). Either int 0..5 or a name alias — see tool description.")
        mode: String = "0",
        @McpDescription("Number of player windows (1-4). Only used for action=play.")
        players: Int = 1,
        @McpDescription("Net mode (only for action=play): standalone | listen | client.")
        netMode: String = "standalone",
        @McpDescription("Launch a dedicated server alongside clients. Only used for action=play.")
        dedicatedServer: Boolean = false,
        @McpDescription("Spawn player at PlayerStart actor. Only used for action=play.")
        spawnAtPlayerStart: Boolean = false,
        @McpDescription("Trigger a code compile before launching PIE. Only used for action=play.")
        compileBeforeRun: Boolean = false,
        @McpDescription("Run all PIE instances (server + clients) inside the editor process. Default true.")
        runUnderOneProcess: Boolean = true,
    ): UnrealPlayResult {
        currentCoroutineContext().reportToolActivity("Play action: $action")
        val host = requireConnected()
        val normalized = action.lowercase()
        if (normalized == "state") {
            return UnrealPlayResult(state = host.playState.name)
        }
        val model = host.model
        val stateService = PlayStateActionStateService.getInstance(currentCoroutineContext().project)
        val requestId = stateService.nextRequestID()
        when (normalized) {
            "play" -> {
                val modeInt = parsePlayMode(mode)
                val net = parseNetMode(netMode)
                if (players !in 1..4) mcpFail("players must be 1-4")
                // New structured signal — superset of the packed-int form. The editor's RiderGameControl
                // reads playSettingsFromRider AND playModeFromRider; sending the struct alone is enough.
                model.playSettingsFromRider.fire(
                    PlaySettings(
                        playMode = modeInt,
                        numberOfClients = players,
                        netMode = net,
                        dedicatedServer = dedicatedServer,
                        spawnAtPlayerStart = spawnAtPlayerStart,
                        compileBeforeRun = compileBeforeRun,
                        runUnderOneProcess = runUnderOneProcess,
                    )
                )
                model.requestPlayFromRider.fire(requestId)
            }
            "pause"      -> model.requestPauseFromRider.fire(requestId)
            "resume"     -> model.requestResumeFromRider.fire(requestId)
            "stop"       -> model.requestStopFromRider.fire(requestId)
            "frame_skip" -> model.requestFrameSkipFromRider.fire(requestId)
            else         -> mcpFail("Unknown action '$action'. Use: state | play | pause | resume | stop | frame_skip")
        }
        return UnrealPlayResult(state = host.playState.name, requested = normalized)
    }

    private fun parsePlayMode(raw: String): Int {
        val trimmed = raw.trim()
        trimmed.toIntOrNull()?.let { n ->
            if (n !in 0..5) mcpFail("mode must be 0-5")
            return n
        }
        return when (trimmed.lowercase()) {
            "viewport", "selected", "selectedviewport"               -> 0
            "mobile", "mobilepreview"                                -> 1
            "floating", "editorfloating", "new-window", "newwindow"  -> 2
            "vr"                                                     -> 3
            "standalone", "newprocess", "process"                    -> 4
            "simulate", "simulation"                                 -> 5
            else -> mcpFail("Unknown mode '$raw'. Use 0-5 or one of: viewport, mobile, floating, vr, standalone, simulate.")
        }
    }

    private fun parseNetMode(raw: String): PlayNetMode = when (raw.trim().lowercase()) {
        "standalone", "single", "" -> PlayNetMode.Standalone
        "listen", "listenserver", "host" -> PlayNetMode.ListenServer
        "client" -> PlayNetMode.Client
        else -> mcpFail("Unknown netMode '$raw'. Use: standalone | listen | client.")
    }

    private fun buildLogFilter(
        category: String?,
        minVerbosity: String?,
        count: Int,
        sinceTimestampMs: Long?,
        pattern: String?,
    ): LogFilter {
        if (count !in 1..1000) mcpFail("count must be between 1 and 1000")
        val regex = pattern?.let {
            try {
                Regex(it)
            } catch (e: Throwable) {
                mcpFail("Invalid regex pattern: ${e.message ?: e::class.simpleName}")
            }
        }
        return LogFilter(
            category = category,
            minVerbosity = minVerbosity,
            count = count,
            sinceTimestampMs = sinceTimestampMs,
            pattern = regex,
        )
    }

    @McpTool
    @McpDescription("""
        |Query log entries streamed from Unreal Editor. Requires Unreal Editor to be connected; the buffer
        |accumulates entries while connected.
        |Filters (all optional, combined with AND):
        |  category         — exact category name match (e.g. "LogTemp").
        |  minVerbosity     — "Fatal" | "Error" | "Warning" | "Display" | "Log" | "Verbose" | "VeryVerbose".
        |  count            — max entries returned (most recent). Default 200, max 1000.
        |  sinceTimestampMs — epoch ms cutoff (entries with timestamp >= cutoff). Useful for resumable follow.
        |  pattern          — regex (Java/Kotlin syntax) matched against entry.message (substring match).
        |Streaming:
        |  follow=true      — long-poll: the call blocks server-side until at least one matching entry lands,
        |                     or `followTimeoutMs` elapses. On timeout an empty batch is returned and the caller
        |                     should poll again with the same `sinceTimestampMs` (use the last received entry's
        |                     timestampMs + 1 to avoid duplicates).
        |  follow=false     — default; one-shot snapshot of the current buffer.
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
        @McpDescription("Regex (Kotlin syntax) matched against entry.message — substring match. Omit for no pattern filter.")
        pattern: String? = null,
        @McpDescription("Long-poll: block until at least one matching entry lands, or followTimeoutMs elapses. Default false.")
        follow: Boolean = false,
        @McpDescription("Maximum time to wait in follow mode, in milliseconds. Default 30000.")
        followTimeoutMs: Long = 30_000L,
    ): UnrealLogResult {
        currentCoroutineContext().reportToolActivity(if (follow) "Following Unreal logs" else "Querying Unreal logs")
        val project = currentCoroutineContext().project
        val filter = buildLogFilter(category, minVerbosity, count, sinceTimestampMs, pattern)
        val buffer = UnrealLogBuffer.getInstance(project)

        if (!follow) {
            return UnrealLogResult(entries = buffer.query(filter))
        }

        if (followTimeoutMs !in 1L..600_000L) mcpFail("followTimeoutMs must be between 1 and 600000")
        val polled: List<UnrealLogEntry>? = withTimeoutOrNull(followTimeoutMs.milliseconds) {
            var snapshot = buffer.query(filter)
            while (snapshot.isEmpty()) {
                delay(250)
                snapshot = buffer.query(filter)
            }
            snapshot
        }
        return UnrealLogResult(entries = polled ?: emptyList())
    }

    @McpTool
    @McpDescription("""
        |One-stop status read for the connected Unreal Editor — combines health + PIE state + recent logs.
        |If the editor isn't connected, only `connected: false` is returned; all other fields are omitted.
        |When connected, returns projectName, processId, current PIE state ("Idle" | "Play" | "Pause"),
        |and the most recent `count` log entries matching the optional category / minVerbosity / pattern /
        |sinceTimestampMs filters (same semantics as ue_get_logs but always one-shot, never follow).
        |Use this when you want a single round-trip pulse instead of separate ue_health + ue_play(state) + ue_get_logs calls.
    """)
    suspend fun ue_status(
        @McpDescription("Maximum recent log entries to include. Default 50.")
        count: Int = 50,
        @McpDescription("Log category name to filter by. Omit for all categories.")
        category: String? = null,
        @McpDescription("Minimum verbosity for the log slice: Fatal | Error | Warning | Display | Log | Verbose | VeryVerbose")
        minVerbosity: String? = null,
        @McpDescription("Regex (Kotlin syntax) matched against entry.message — substring match. Omit for no pattern filter.")
        pattern: String? = null,
        @McpDescription("Epoch milliseconds — only entries at or after this timestamp.")
        sinceTimestampMs: Long? = null,
    ): UnrealStatusResult {
        currentCoroutineContext().reportToolActivity("Querying Unreal status")
        val project = currentCoroutineContext().project
        val host = UnrealHost.getInstance(project)
        val info = host.connectionInfo
        val processAlive = info?.processId?.let { pid ->
            ProcessHandle.of(pid.toLong()).map { it.isAlive }.orElse(false)
        } ?: false
        val connected = host.isConnectedToUnrealEditor && processAlive
        if (!connected) {
            return UnrealStatusResult(connected = false)
        }
        val filter = buildLogFilter(category, minVerbosity, count, sinceTimestampMs, pattern)
        val logs = UnrealLogBuffer.getInstance(project).query(filter)
        return UnrealStatusResult(
            connected = true,
            projectName = info?.projectName,
            processId = info?.processId,
            playState = host.playState.name,
            recentLogs = logs,
        )
    }

    @McpTool
    @McpDescription("""
        |Execute one or more Python scripts inside Unreal Editor using the built-in Python plugin.
        |Scripts run on the editor's game thread with full access to the Unreal Python API.
        |Provide EXACTLY one of:
        |  script  — a single Python source string. `isolated=true` runs it in EvaluateStatement mode
        |            (returns expression value).
        |  scripts — a list of Python sources to execute sequentially with resume-on-failure.
        |            `startFrom` resumes from a 0-based index (use lastSuccessfulIndex+1 from a previous
        |            failed call). `isolated` is ignored for batch.
        |Always returns a batch-shaped result; a single-script call returns a 1-item batch.
        |Output of each script is capped at 10,000 characters to avoid context overflow.
        |Reference: https://dev.epicgames.com/documentation/en-us/unreal-engine/python-api/
    """)
    suspend fun ue_execute_python(
        @McpDescription("Single Python source to execute. Mutually exclusive with `scripts`.")
        script: String? = null,
        @McpDescription("List of Python sources to execute sequentially. Mutually exclusive with `script`.")
        scripts: List<String>? = null,
        @McpDescription("0-based index to resume the batch from (default 0). Ignored when `script` is set.")
        startFrom: Int = 0,
        @McpDescription("Run a single script in isolated scope (EvaluateStatement mode). Ignored for batch.")
        isolated: Boolean = false,
    ): UnrealBatchScriptResult {
        currentCoroutineContext().reportToolActivity("Executing Python in UE")
        val host = requireConnected()
        if ((script == null) == (scripts == null)) {
            mcpFail("Provide exactly one of `script` or `scripts`.")
        }
        return if (script != null) {
            val result = host.model.executeScript.startSuspending(
                ScriptRequest(script = FString(script), isolated = isolated)
            )
            UnrealBatchScriptResult(
                results = listOf(
                    UnrealScriptResult(
                        success = result.success,
                        output = result.output.data,
                        result = result.result.data,
                        error = result.error.data.takeIf { it.isNotEmpty() },
                    )
                ),
                lastSuccessfulIndex = if (result.success) 0 else -1,
            )
        } else {
            val batch = host.model.executeBatchScripts.startSuspending(
                BatchScriptRequest(
                    scripts = scripts!!.map { FString(it) },
                    startFrom = startFrom,
                )
            )
            UnrealBatchScriptResult(
                results = batch.results.map { r ->
                    UnrealScriptResult(
                        success = r.success,
                        output = r.output.data,
                        result = r.result.data,
                        error = r.error.data.takeIf { it.isNotEmpty() },
                    )
                },
                lastSuccessfulIndex = batch.lastSuccessfulIndex,
            )
        }
    }
}
