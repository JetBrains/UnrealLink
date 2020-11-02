package model.editorPlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.util.syspropertyOrInvalid
import model.lib.ue4.UE4Library
import java.io.File

@Suppress("unused")
object RdEditorRoot : Root(
        // TODO: use settings for precompiled headers when available and remove hardcoded generator
        Cpp17Generator(FlowTransform.Reversed, "", File(syspropertyOrInvalid("model.out.src.editorPlugin.cpp.dir")), generatePrecompiledHeaders = false)
) {
    init {
        setting(CSharp50Generator.Namespace, "RiderPlugin.UnrealLink.Model.BackendUnreal")
        setting(Cpp17Generator.Namespace, "JetBrains::EditorPlugin")

        setting(Cpp17Generator.AdditionalHeaders, listOf("UE4TypesMarshallers.h"))
        setting(Cpp17Generator.ExportMacroName,  "RIDERLINK_API")
    }
}

@Suppress("unused")
object RdEditorModel : Ext(RdEditorRoot) {
    init {
        signal("unrealLog", UE4Library.UnrealLogEvent).async
        property("playMode", int)
        source("frameSkip", void)

        signal("openBlueprint", UE4Library.BlueprintReference)

        signal("onBlueprintAdded", UE4Library.UClass).async
        call("isBlueprintPathName", UE4Library.FString, bool)
        call("getPathNameByPath", UE4Library.FString, UE4Library.FString.nullable)

        callback("AllowSetForegroundWindow", int, bool)

        sink("PlayStateFromEditor", UE4Library.PlayState)
        source("PlayStateFromRider", UE4Library.PlayState)
    }
}
