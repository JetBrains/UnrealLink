package model.lib.ue4

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.cpp.CppIntrinsicType
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rd.generator.nova.util.syspropertyOrInvalid
import model.editorPlugin.RdEditorRoot
import model.rider.RdRiderModel
import java.io.File

object UE4Library : Root(
        CSharp50Generator(FlowTransform.AsIs, "JetBrains.Unreal.Lib", File(syspropertyOrInvalid("model.out.src.lib.ue4.csharp.dir"))),
        Cpp17Generator(FlowTransform.Reversed, "Jetbrains.EditorPlugin", File(syspropertyOrInvalid("model.out.src.lib.ue4.cpp.dir"))),
        Kotlin11Generator(FlowTransform.Symmetric, "com.jetbrains.rider.model", File(syspropertyOrInvalid("model.out.src.lib.ue4.kt.dir")))
) {
    init {
        setting(Cpp17Generator.MarshallerHeaders, listOf("UE4TypesMarshallers.h"))
    }

    private fun <T : Declaration> declare(intrinsic: CppIntrinsicType, factory: Toplevel.() -> T): T {
        return this.factory().apply {
            setting(Cpp17Generator.Intrinsic, intrinsic)
            setting(CSharp50Generator.Namespace, "JetBrains.Unreal.Lib")
        }
    }

    private val StringRange = structdef("StringRange") {
        field("left", PredefinedType.int)
        field("right", PredefinedType.int)
    }

    val FString = declare(CppIntrinsicType("FString", "Runtime/Core/Public/Containers/UnrealString.h")) {
        structdef("FString") {
            field("data", string)
        }
    }

    val VerbosityType = declare(CppIntrinsicType("ELogVerbosity::Type", "Logging/LogVerbosity.h")) {
        enum("VerbosityType") {
            +("NoLogging").doc("= 0")


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

            const("All", int, 0x40).doc("=VeryVerbose")
//            +"NumVerbosity"
            const("VerbosityMask", int, 0xf)
            const("SetColor", int, 0x40).doc("not actually a verbosity, used to set the color of an output device")
            const("BreakOnLog", int, 0x80)
        }
    }


    val UnrealLogMessage = structdef("UnrealLogMessage") {
        field("message", FString)
        field("type", VerbosityType)
        field("category", FString)
        field("time", dateTime.nullable)
    }

    val BlueprintHighlighter = structdef("BlueprintHighlighter") {
        field("start", int)
        field("end", int)
    }

    val BlueprintStruct = structdef("BlueprintStruct") {
        field("pathName", FString)
        field("graphName", FString)
    }

//    val UnrealFilterProvider = aggregatedef("UnrealFilterProvider") {
//        call("isBlueprint", FString, bool).readonly.async
//        signal("navigate", FString)
//    }
}

