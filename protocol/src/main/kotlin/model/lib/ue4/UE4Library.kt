package model.lib.ue4

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.dateTime
import com.jetbrains.rd.generator.nova.PredefinedType.string
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.cpp.CppIntrinsicType
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import model.editorPlugin.RdEditorRoot

object UE4Library : Root(*RdEditorRoot.generators) {
    init {
        setting(CSharp50Generator.Namespace, "JetBrains.Unreal.Lib")
    }

    private fun <T : Declaration> declare(intrinsic: CppIntrinsicType, factory: Toplevel.() -> T): T {
        return this.factory().apply {
            setting(Cpp17Generator.Intrinsic, intrinsic)
            setting(CSharp50Generator.Namespace, "JetBrains.Unreal.Lib")
        }
    }

    val FString = declare(CppIntrinsicType("FString", "Runtime/Core/Public/Containers/UnrealString.h")) {
        structdef("FString") {
            field("data", string)
        }
    }

    val VerbosityType = declare(CppIntrinsicType("ELogVerbosity::Type", "Logging/LogVerbosity.h")) {
        enum("VerbosityType") {
            +"NoLogging" //		= 0,

            /** Always prints a fatal error to console (and log file) and crashes (even if logging is disabled) */
            +"Fatal"

            /**
             * Prints an error to console (and log file).
             * Command lets and the editor collect and report errors. Error messages result in command let failure.
             */
            +"Error"

            /**
             * Prints a warning to console (and log file).
             * Command lets and the editor collect and report warnings. Warnings can be treated as an error.
             */
            +"Warning"

            /** Prints a message to console (and log file) */
            +"Display"

            /** Prints a message to a log file (does not print to console) */
            +"Log"

            /**
             * Prints a verbose message to a log file (if Verbose logging is enabled for the given category,
             * usually used for detailed logging)
             */
            +"Verbose"

            /**
             * Prints a verbose message to a log file (if VeryVerbose logging is enabled,
             * usually used for detailed logging that would otherwise spam output)
             */
            +"VeryVerbose"

            // Log masks and special Enum values

            +"All"//				= VeryVerbose,
            +"NumVerbosity"
            +"VerbosityMask"//	= 0xf,
            +"SetColor"//		= 0x40, // not actually a verbosity, used to set the color of an output device
            +"BreakOnLog"//		= 0x80
        }
    }

    val UnrealLogMessage = structdef("UnrealLogMessage") {
        field("message", FString)
        field("type", VerbosityType)
        field("category", FString)
        field("time", dateTime.nullable)
    }
}

