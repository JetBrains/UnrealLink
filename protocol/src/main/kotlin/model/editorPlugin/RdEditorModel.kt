package model.editorPlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import model.lib.ue4.UE4Library

@Suppress("unused")
object RdEditorRoot : Root() {
    init {
        setting(CSharp50Generator.Namespace, "RiderPlugin.UnrealLink.Model.BackendUnreal")
        setting(Cpp17Generator.Namespace, "JetBrains::EditorPlugin")

        setting(Cpp17Generator.AdditionalHeaders, listOf("UE4TypesMarshallers.h"))
        setting(Cpp17Generator.ExportMacroName,  "RIDERLINK_API")
        setting(Cpp17Generator.GeneratePrecompiledHeaders, false)
        setting(Cpp17Generator.UsePrecompiledHeaders, false)
    }
}

@Suppress("unused")
object RdEditorModel : Ext(RdEditorRoot) {
    init {
        signal("unrealLog", UE4Library.UnrealLogEvent).async

        signal("openBlueprint", UE4Library.BlueprintReference)

        signal("onBlueprintAdded", UE4Library.UClass).async
        call("isBlueprintPathName", UE4Library.FString, bool)
        call("getPathNameByPath", UE4Library.FString, UE4Library.FString.nullable)

        callback("AllowSetForegroundWindow", int, bool)

        property("isGameControlModuleInitialized", false).readonly
        sink("PlayStateFromEditor", UE4Library.PlayState)
        source("RequestPlayFromRider", int)
        source("RequestPauseFromRider", int)
        source("RequestResumeFromRider", int)
        source("RequestStopFromRider", int)
        source("RequestFrameSkipFromRider", int)
        sink("NotificationReplyFromEditor", UE4Library.RequestResultBase)

        sink("PlayModeFromEditor", int)
        source("PlayModeFromRider", int)
    }
}
