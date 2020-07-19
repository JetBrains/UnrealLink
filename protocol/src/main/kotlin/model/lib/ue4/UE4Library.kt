package model.lib.ue4

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.cpp.CppIntrinsicType
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rd.generator.nova.util.syspropertyOrInvalid
import java.io.File

object UE4Library : Root(
        CSharp50Generator(FlowTransform.AsIs, "JetBrains.Unreal.Lib", File(syspropertyOrInvalid("model.out.src.lib.ue4.csharp.dir"))),
        Cpp17Generator(FlowTransform.Reversed, "Jetbrains::EditorPlugin", File(syspropertyOrInvalid("model.out.src.lib.ue4.cpp.dir")), generatedFileSuffix = ""),
        Kotlin11Generator(FlowTransform.Symmetric, "com.jetbrains.rider.model", File(syspropertyOrInvalid("model.out.src.lib.ue4.kt.dir")))
) {
    init {
        setting(Cpp17Generator.AdditionalHeaders, listOf(
                "UE4TypesMarshallers.h",
                "Runtime/Core/Public/Containers/Array.h",
                "Runtime/Core/Public/Containers/ContainerAllocationPolicies.h"
        ))
        setting(Cpp17Generator.ListType, CppIntrinsicType(null, "TArray", "Runtime/Core/Public/Containers/Array.h"))
        setting(Cpp17Generator.AllocatorType) { "FDefaultAllocator" }
        setting(Cpp17Generator.ExportMacroName,  "RIDERLINK_API")
    }

    private fun <T : Declaration> declare(intrinsic: CppIntrinsicType, factory: Toplevel.() -> T): T {
        return this.factory().apply {
            intrinsic.namespace?.let { ns ->
                setting(Cpp17Generator.Namespace, ns)
            }
            setting(Cpp17Generator.Intrinsic, intrinsic)
            setting(CSharp50Generator.Namespace, "JetBrains.Unreal.Lib")
        }
    }

    val StringRange = structdef("StringRange") {
        field("first", int)
        field("last", int)
    }

    val FString = declare(CppIntrinsicType(null, "FString", "Runtime/Core/Public/Containers/UnrealString.h")) {
        structdef("FString") {
            field("data", string)
        }
    }

    val PlayState = enum("PlayState") {
        +"Idle"
        +"Play"
        +"Pause"
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
    val BlueprintHighlighter = structdef("BlueprintHighlighter") {
        field("begin", int)
        field("end", int)
    }

    val BlueprintReference = structdef("BlueprintReference") {
        field("pathName", FString)
    }
}

