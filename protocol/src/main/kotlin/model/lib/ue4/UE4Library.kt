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
        Cpp17Generator(FlowTransform.Reversed, "Jetbrains::EditorPlugin", File(syspropertyOrInvalid("model.out.src.lib.ue4.cpp.dir"))),
        Kotlin11Generator(FlowTransform.Symmetric, "com.jetbrains.rider.model", File(syspropertyOrInvalid("model.out.src.lib.ue4.kt.dir")))
) {
    init {
        setting(Cpp17Generator.MarshallerHeaders, listOf("UE4TypesMarshallers.h"))
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

    private val StringRange = structdef("StringRange") {
        field("left", int)
        field("right", int)
    }

    val FString = declare(CppIntrinsicType(null, "FString", "Runtime/Core/Public/Containers/UnrealString.h")) {
        structdef("FString") {
            field("data", string)
        }
    }

    val FStringArray = declare(CppIntrinsicType(null, "TArray<FString>", "Runtime/Core/Public/Containers/Array.h")) {
        structdef("FStringArray") {
            field("data", array(string))
        }
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

    val LogEvent = basestruct("LogEvent") {
        field("lineNumber", int)
    }

    private val LogMessage = structdef("LogMessage") {
        field("text", FString)
        field("type", VerbosityType)
        field("category", FString)
        field("time", dateTime.nullable)
    }

    @Suppress("unused")
    private val LogMessageEvent = structdef("LogMessageEvent") extends LogEvent {
        field("message", LogMessage)
    }

    private val BlueprintFunction = structdef("BlueprintFunction") {
        field("class", BlueprintClass)
        field("methodName", FString)
    }

    private val ScriptCallStackFrame = structdef("ScriptCallStackFrame") {
        field("header", FString)
        field("blueprintFunction", BlueprintFunction)
    }

    private val IScriptCallStack = basestruct("IScriptCallStack") {
    }

    @Suppress("unused")
    private val EmptyScriptCallStack = structdef("EmptyScriptCallStack") extends IScriptCallStack {
        field("header", FString)
    }

    @Suppress("unused")
    private val ScriptCallStack = structdef("ScriptCallStack") extends IScriptCallStack {
        field("header", FString)
        field("frames", immutableList(ScriptCallStackFrame))
    }

    private val UnableToDisplayScriptCallStack = structdef("UnableToDisplayScriptCallStack") extends IScriptCallStack {}

    @Suppress("unused")
    private val ScriptCallStackEvent = structdef("ScriptCallStackEvent") extends LogEvent {
        field("ScriptCallStack", IScriptCallStack)
    }

    val BlueprintHighlighter = structdef("BlueprintHighlighter") {
        field("begin", int)
        field("end", int)
    }

    val BlueprintClass = structdef("BlueprintClass") {
        field("name", FString)
    }
}

