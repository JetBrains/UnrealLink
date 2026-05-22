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
        property("connectionInfo", UE4Library.ConnectionInfo).readonly

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

        // Structured PIE settings — supersedes the packed-int signals above for callers
        // that need PlayNetMode / RunUnderOneProcess. Old int signals are kept for the
        // legacy editor UI handlers.
        sink("PlaySettingsFromEditor", UE4Library.PlaySettings)
        source("PlaySettingsFromRider", UE4Library.PlaySettings)

        // Hot Reload here is not Unreal's HotReload but generic Hot Reload mechanism which can be either Unreal's HotReload or Unreal's LiveCoding
        property("IsHotReloadAvailable", false).readonly
        property("IsHotReloadCompiling", false).readonly
        source("TriggerHotReload", void)

        // Python execution — C# calls these after forwarding from RdRiderModel; C++ implements handlers
        call("executeScript", UE4Library.ScriptRequest, UE4Library.ScriptResult).async
        call("executeBatchScripts", UE4Library.BatchScriptRequest, UE4Library.BatchScriptResult).async

        // Live asset search — C# forwards from RdRiderModel.searchUnrealAssetsLive;
        // C++ binds in AssetRegistrySearcher and queries IAssetRegistry on the game thread.
        call("searchAssetsLive", UE4Library.AssetLiveSearchRequest, UE4Library.AssetLiveSearchResponse).async

        // Screenshots — C# forwards from RdRiderModel.takeScreenshot;
        // C++ binds in ScreenshotCapturer using FSlateApplication::TakeScreenshot
        // for window/viewport and ThumbnailTools for asset preview cache.
        call("takeScreenshot", UE4Library.ScreenshotRequest, UE4Library.ScreenshotResult).async
    }
}
