package model.lib.ue4

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.cpp.CppIntrinsicType
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator

object UE4Library : Root() {
    init {
        setting(Kotlin11Generator.Namespace, "com.jetbrains.rider.plugins.unreal.model")
        setting(CSharp50Generator.Namespace, "RiderPlugin.UnrealLink.Model")
        setting(Cpp17Generator.Namespace, "JetBrains::EditorPlugin")

        setting(Cpp17Generator.AdditionalHeaders, listOf(
                "UE4TypesMarshallers.h",
                "Runtime/Core/Public/Containers/Array.h",
                "Runtime/Core/Public/Containers/ContainerAllocationPolicies.h"
        ))
        setting(Cpp17Generator.ListType, CppIntrinsicType(null, "TArray", "Containers/Array.h"))
        setting(Cpp17Generator.AllocatorType) { "FDefaultAllocator" }
        setting(Cpp17Generator.ExportMacroName,  "RIDERLINK_API")
        setting(Cpp17Generator.GeneratePrecompiledHeaders, false)
        setting(Cpp17Generator.UsePrecompiledHeaders, false)
    }

    private fun <T : Declaration> declare(intrinsic: CppIntrinsicType, factory: Toplevel.() -> T): T {
        return this.factory().apply {
            intrinsic.namespace?.let { ns ->
                setting(Cpp17Generator.Namespace, ns)
            }
            setting(Cpp17Generator.Intrinsic, intrinsic)
        }
    }

    val StringRange = structdef("StringRange") {
        field("first", int)
        field("last", int)
    }

    val FString = declare(CppIntrinsicType(null, "FString", "Containers/UnrealString.h")) {
        structdef("FString") {
            field("data", string)
        }
    }

    val PlayState = enum("PlayState") {
        +"Idle"
        +"Play"
        +"Pause"
    }

    val RequestResultBase = basestruct("RequestResultBase") {
        field("requestID", int)
    }

    @Suppress("unused")
    val RequestSucceed = structdef("RequestSucceed") extends RequestResultBase {
    }

    @Suppress("unused")
    val RequestFailed = structdef("RequestFailed") extends RequestResultBase {
        field("type", enum("NotificationType") {
            +"Message"
            +"Error"
        })
        field("message", FString)
    }

    val VerbosityType = declare(CppIntrinsicType("ELogVerbosity", "Type", "Logging/LogVerbosity.h"))
    {
        setting(Cpp17Generator.IsNonScoped, "uint8")

        enum("VerbosityType") {
            (+"NoLogging").doc("= 0")


            (+"Fatal").doc(
                    "Always prints a fatal error to console (and log file) and crashes (even if logging is disabled)"
            )

            (+"Error").doc(
                    "Prints an error to console (and log file)." +
                            "Command lets and the editor collect and report errors. Error messages result in command let failure."
            )

            (+"Warning").doc(
                    "Prints a warning to console (and log file)." +
                            "Command lets and the editor collect and report warnings. Warnings can be treated as an error.")

            (+"Display").doc(
                    "Prints a message to console (and log file)"
            )

            (+"Log").doc("Prints a message to a log file (does not print to console)")

            (+"Verbose").doc(
                    "Prints a verbose message to a log file (if Verbose logging is enabled for the given category, usually used for detailed logging)"
            )

            (+"VeryVerbose").doc(
                    "Prints a verbose message to a log file (if VeryVerbose logging is enabled, usually used for detailed logging that would otherwise spam output)"
            )

            // Log masks and special Enum values

            (+"All")/*.setting(Cpp17Generator.EnumConstantValue, 7)*/.doc("=VeryVerbose")
            (+"NumVerbosity")
            (+"VerbosityMask").setting(Cpp17Generator.EnumConstantValue, 0xf)
            (+"SetColor").setting(Cpp17Generator.EnumConstantValue, 0x40).doc("not actually a verbosity, used to set the color of an output device")
            (+"BreakOnLog").setting(Cpp17Generator.EnumConstantValue, 0x80)
        }
    }

    private val LogMessageInfo = structdef("LogMessageInfo") {
        field("type", VerbosityType)
        field("category", FString)
        field("time", dateTime.nullable)
    }

    val UnrealLogEvent = structdef("UnrealLogEvent") {
        field("info", LogMessageInfo)
        field("text", FString)
        field("bpPathRanges", immutableList(StringRange))
        field("methodRanges", immutableList(StringRange))
    }

    /*@Suppress("unused")
    private val LogMessageEvent = structdef("LogMessageEvent") extends LogEvent {
        field("message", FString)
    }*/

    val UClass = structdef("UClass") {
        field("name", FString)
    }

    @Suppress("unused")
    val BlueprintFunction = structdef("BlueprintFunction") {
        field("class", UClass)
        field("name", FString)

        const("separator", string, ":")
    }

    //region Script Call Stack
    private val ScriptCallStackFrame = structdef("ScriptCallStackFrame") {
        //        field("header", FString)
//        field("blueprintFunction", BlueprintFunction)
        field("entry", FString)
    }

    private val IScriptCallStack = basestruct("IScriptCallStack") {
        const("header", string, "Script call stack:")
    }

    @Suppress("unused")
    private val EmptyScriptCallStack = structdef("EmptyScriptCallStack") extends IScriptCallStack {
        const("message", string, "Script call stack: [Empty] (FFrame::GetStackTrace() called from native code)")
    }

    @Suppress("unused")
    private val ScriptCallStack = structdef("ScriptCallStack") extends IScriptCallStack {
        field("frames", immutableList(ScriptCallStackFrame))
    }

    @Suppress("unused")
    private val UnableToDisplayScriptCallStack = structdef("UnableToDisplayScriptCallStack") extends IScriptCallStack {
        const("message", string, "Unable to display Script Callstack. Compile with DO_BLUEPRINT_GUARD=1")
    }

    //endregion

    //region ScriptMsg
    private val IScriptMsg = basestruct("IScriptMsg") {
        const("header", string, "Script msg:")
    }

    @Suppress("unused")
    private val ScriptMsgException = structdef("ScriptMsgException") extends IScriptMsg {
        field("message", FString)
    }

    @Suppress("unused")
    private val ScriptMsgCallStack = structdef("ScriptMsgCallStack") extends IScriptMsg {
        field("message", FString)
        field("scriptCallStack", IScriptCallStack)
    }

/*
    @Suppress("unused")
    private val ScriptMsgEvent = structdef("ScriptMsgEvent") extends LogPart {
        field("scriptMsg", IScriptMsg)
    }
*/

    //endregion
    @Suppress("unused")
    val BlueprintHighlighter = structdef("BlueprintHighlighter") {
        field("begin", int)
        field("end", int)
    }

    val BlueprintReference = structdef("BlueprintReference") {
        field("pathName", FString)
        field("guid", FString)
    }

    val ConnectionInfo = structdef("ConnectionInfo") {
        field("projectName", string)
        field("executableName", string)
        field("processId", int)
    }

    // ── Python execution shared types ─────────────────────────────────────────

    val ScriptRequest = structdef("ScriptRequest") {
        field("script", FString)
        field("isolated", bool).default(false)
    }

    val ScriptResult = structdef("ScriptResult") {
        field("success", bool)
        field("output", FString)
        field("result", FString)
        field("error", FString)
    }

    val BatchScriptRequest = structdef("BatchScriptRequest") {
        field("scripts", immutableList(FString))
        field("startFrom", int).default(0)
    }

    val BatchScriptResult = structdef("BatchScriptResult") {
        field("results", immutableList(ScriptResult))
        field("lastSuccessfulIndex", int)
    }

    // ── Live asset search (UE Editor AssetRegistry) ──────────────────────────

    val AssetLiveSearchRequest = structdef("AssetLiveSearchRequest") {
        field("query", FString.nullable)
        field("baseClass", FString.nullable)
        field("packagePath", FString.nullable)
        field("limit", int).default(200)
    }

    val AssetLiveSearchAsset = structdef("AssetLiveSearchAsset") {
        field("assetPath", FString)
        field("assetName", FString)
        field("baseClass", FString.nullable)
        // assetClass = the asset's own class short name (e.g. "Blueprint", "World").
        field("assetClass", FString.nullable)
    }

    val AssetLiveSearchResponse = structdef("AssetLiveSearchResponse") {
        field("assets", immutableList(AssetLiveSearchAsset))
    }

    // ── PIE settings ─────────────────────────────────────────────────────────
    // Replaces the legacy packed-int playModeFromRider/Editor signals with a
    // structured representation so the new fields (netMode, runUnderOneProcess)
    // don't need bit-packing tricks. Legacy signals stay in the models for the
    // existing UI handlers in PlaySettingsActions.kt.

    val PlayNetMode = enum("PlayNetMode") {
        +"Standalone"      // EPlayNetMode::PIE_Standalone (0)
        +"ListenServer"    // EPlayNetMode::PIE_ListenServer (1)
        +"Client"          // EPlayNetMode::PIE_Client (2)
    }

    val PlaySettings = structdef("PlaySettings") {
        field("playMode", int)             // EPlayModeType index 0..6 (Viewport=0, Floating=2, NewProcess=4, Simulate=5, …)
        field("numberOfClients", int)      // 1..4
        field("netMode", PlayNetMode)
        field("dedicatedServer", bool)
        field("spawnAtPlayerStart", bool)
        field("compileBeforeRun", bool)
        field("runUnderOneProcess", bool)
    }

    // ── Screenshots ──────────────────────────────────────────────────────────
    // Editor-side capture of the active level viewport, the focused editor
    // window, or an asset preview pane. PNG bytes are written to
    // <Project>/Saved/Screenshots/RiderMCP/ and the absolute path is returned
    // in the result — no binary blob travels through RD.

    val ScreenshotKind = enum("ScreenshotKind") {
        +"EditorWindow"   // active top-level editor window (chrome + viewport + panels)
        +"Viewport"       // active level-editor viewport only
        +"AssetPreview"   // preview pane in BP/Material/Anim/Niagara asset editor
    }

    val ScreenshotRequest = structdef("ScreenshotRequest") {
        field("kind", ScreenshotKind)
        // Required when kind == AssetPreview (long package path, e.g. /Game/Foo/BP_Hero).
        field("assetPath", FString.nullable)
        // Pixel dimensions of the rendered PNG. 0 ⇒ use the widget's native size.
        field("width", int).default(0)
        field("height", int).default(0)
        // AssetPreview only: skip the thumbnail-cache fast path and force a
        // live editor capture (opens the asset editor if not already open).
        field("forceLive", bool).default(false)
    }

    val ScreenshotResult = structdef("ScreenshotResult") {
        field("success", bool)
        // Absolute path to the written PNG. Empty on failure.
        field("path", FString)
        field("width", int)
        field("height", int)
        // Diagnostic label for which capture path produced this result
        // (e.g. "SlateApplication.TakeScreenshot", "ThumbnailTools.Cache",
        // "AutomationLibrary.HighResScreenshot"). Helps callers reason about
        // chrome-vs-viewport semantics and cache vs live source.
        field("sourceApi", FString)
        // Human-readable diagnostic. Empty on success.
        field("error", FString)
    }

    // ── Viewport camera ──────────────────────────────────────────────────────
    // Editor-side level viewport camera control. Maps 1:1 to
    // UUnrealEditorSubsystem::Get/SetLevelViewportCameraInfo plus framing
    // helpers. Doubles match UE 5.x Large-World-Coordinates FVector/FRotator.

    val Vector3 = structdef("Vector3") {
        field("x", double)
        field("y", double)
        field("z", double)
    }

    val Rotator3 = structdef("Rotator3") {
        field("pitch", double)
        field("yaw", double)
        field("roll", double)
    }

    val ViewportCameraAction = enum("ViewportCameraAction") {
        +"Get"
        +"Set"
        +"Move"
        +"LookAt"
        +"FocusOnActor"
    }

    // One request struct services every action; fields that don't apply to the
    // requested action are simply ignored on the C++ side.
    //   Get               — no fields used.
    //   Set               — location and/or rotation replace the current value (others stay).
    //   Move              — delta + (relative=true ⇒ x/y/z mean forward/right/up); rotationDelta optional.
    //   LookAt            — target is required; location stays put.
    //   FocusOnActor      — actorName is required; minDistance lower-bounds the framing distance.
    val ViewportCameraRequest = structdef("ViewportCameraRequest") {
        field("action", ViewportCameraAction)
        field("location", Vector3.nullable)
        field("rotation", Rotator3.nullable)
        field("delta", Vector3.nullable)
        field("relative", bool).default(false)
        field("rotationDelta", Rotator3.nullable)
        field("target", Vector3.nullable)
        field("actorName", FString.nullable)
        // 0.0 ⇒ use the C++-side automatic framing distance (3×actor extent).
        field("minDistance", double)
    }

    val ViewportCameraResponse = structdef("ViewportCameraResponse") {
        field("success", bool)
        // Post-action pose. On `Get`, this is the current pose. On other
        // actions, the pose that was set / computed. On failure, zero-init.
        field("location", Vector3)
        field("rotation", Rotator3)
        // FocusOnActor: the actor label that was actually resolved (may differ
        // from the requested name if a fallback was applied). Null otherwise.
        field("actorResolved", FString.nullable)
        // Human-readable diagnostic. Empty on success.
        field("error", FString)
    }

    // ── Input simulation ─────────────────────────────────────────────────────
    // Drive PIE player input. Three modes: high-level action sequence,
    // low-level sustained primitive, Enhanced Input injection. Each new arm
    // cancels any in-flight ticker (see "callback lifetime" pitfall in the
    // simulate-user-input recipe). The C++ side requires a live PIE world and
    // a possessed pawn; non-PIE calls fail fast with a clear error.
    //
    // String-typed enums on the wire mirror the screenshot tool's `kind`
    // pattern — keeps the protocol surface lean and lets the MCP layer
    // accept aliases ("forward" / "Forward" / "FWD") without polluting the
    // generated enum.

    val InputActionEntry = structdef("InputActionEntry") {
        // "move" | "jump" | "look" | "wait"
        field("type", FString)
        // "forward" | "back" | "left" | "right" — required for "move".
        field("direction", FString.nullable)
        // Movement input scale (1.0 = full input).
        field("scale", double)
        // Look: per-action yaw / pitch totals in degrees, spread across `duration`.
        field("yaw", double)
        field("pitch", double)
        // Seconds the action holds before the sequencer advances.
        field("duration", double)
    }

    val InputSimulationRequest = structdef("InputSimulationRequest") {
        // "actions" | "primitive" | "enhanced"
        field("mode", FString)

        // Actions mode — sequenced list driven by a single tick-callback.
        field("actions", immutableList(InputActionEntry))

        // Primitive mode — one sustained call per frame for `primitiveDuration`.
        // "add_movement_input" | "add_yaw_input" | "add_pitch_input" | "jump"
        field("primitiveCall", FString.nullable)
        // "forward" | "back" | "left" | "right" | "world_vec" (for add_movement_input).
        field("primitiveDirection", FString.nullable)
        // Used only when primitiveDirection == "world_vec".
        field("primitiveWorldVec", Vector3.nullable)
        field("primitiveScale", double)
        field("primitiveValue", double)
        field("primitiveDuration", double)

        // Enhanced Input mode — calls inject_input_for_action / start/stop
        // continuous injection via UEnhancedInputLocalPlayerSubsystem.
        // Long package path, e.g. "/Game/Input/Actions/IA_Move".
        field("enhancedAssetPath", FString.nullable)
        // "axis2d" | "axis1d" | "bool"
        field("enhancedValueKind", FString.nullable)
        field("enhancedAxis2dX", double)
        field("enhancedAxis2dY", double)
        field("enhancedAxis1d", double)
        field("enhancedBool", bool)
        // true ⇒ stop the previously-armed continuous injection for this asset.
        field("enhancedClear", bool)
    }

    val InputSimulationResponse = structdef("InputSimulationResponse") {
        field("success", bool)
        // True when a ticker was registered (actions/primitive) or the EIS
        // call dispatched (enhanced). For one-shot primitives (`jump`,
        // duration <= 0) the call is applied immediately and `armed` is false.
        field("armed", bool)
        // Pawn pose at arm time. Caller polls / sleeps then diffs.
        field("startLocation", Vector3.nullable)
        field("startVelocity", Vector3.nullable)
        // Actions mode: count of actions queued. Other modes: 0.
        field("nActions", int)
        field("error", FString)
    }
}

