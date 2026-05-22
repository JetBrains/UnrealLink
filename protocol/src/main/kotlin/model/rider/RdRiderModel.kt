package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel
import model.lib.ue4.UE4Library

@Suppress("unused")
object RdRiderModel : Ext(SolutionModel.Solution) {
    init {
        setting(Kotlin11Generator.Namespace, "com.jetbrains.rider.plugins.unreal.model.frontendBackend")
        setting(CSharp50Generator.Namespace, "RiderPlugin.UnrealLink.Model.FrontendBackend")
    }

    private val LinkRequest = structdef("LinkRequest") {
        field("data", UE4Library.FString)
    }

    val ILinkResponse = basestruct("ILinkResponse") {}

    val LinkResponseBlueprint = structdef("LinkResponseBlueprint") extends ILinkResponse {
        field("fullPath", UE4Library.FString)
        field("range", UE4Library.StringRange)
    }

    val LinkResponseFilePath = structdef("LinkResponseFilePath") extends ILinkResponse {
        field("fullPath", UE4Library.FString)
        field("range", UE4Library.StringRange)
    }

    val LinkResponseUnresolved = structdef("LinkResponseUnresolved") extends ILinkResponse {}

    private val MethodReference = structdef("MethodReference") {
        field("class", UE4Library.UClass)
        field("method", UE4Library.FString)

        const("separator", string, "::")
    }

    private val PluginInstallStatus = enum("PluginInstallStatus") {
        +"NoPlugin"
        +"UpToDate"
        +"InEngine"
        +"InGame"
    }


    private val EditorPluginOutOfSync = structdef("EditorPluginOutOfSync") {
        field("status", PluginInstallStatus)
        field("IsGameAvailable", bool)
    }

    private val InstallMessage = structdef("InstallMessage") {
        field("text", string)
        field("type", enum("ContentType") {
            +"Normal"
            +"Error"
        })
    }

    private val InstallPluginDescription = structdef("InstallPluginDescription") {
        field("location", enum("PluginInstallLocation") {
            +"Engine"
            +"Game"
            +"NotInstalled"
        })
        field("forceInstall", enum("ForceInstall") {
            +"Yes"
            +"No"
        })
        field("buildRequired", bool).default(true)
        field("selectedUprojectPaths", immutableList(string))
        field("unselectedUprojectPaths", immutableList(string))
    }

    private val GamePluginInstallInfo = structdef("GamePluginInstallInfo") {
        field("projectName", string)
        field("uprojectPath", string)
        field("isPluginAvailable", bool)
        field("isPluginSynced", bool)
    }

    private val UnrealAssetInfo = structdef("UnrealAssetInfo") {
        field("assetPath", string)
        field("assetName", string)
        field("baseClass", string.nullable)
    }
    private val UnrealAssetSearchRequest = structdef("UnrealAssetSearchRequest") {
        field("query", string.nullable)
        field("baseClass", string.nullable)
        field("packagePath", string.nullable)
        field("limit", int).default(200)
    }
    private val UnrealAssetSearchResponse = structdef("UnrealAssetSearchResponse") {
        field("assets", immutableList(UnrealAssetInfo))
    }
    // Live-path-only asset info: the editor knows the asset's own class
    // ("Blueprint", "AnimBlueprint", "World", …) without parsing the .uasset
    // header, so we carry it through. The cache path leaves this null.
    private val UnrealAssetLiveInfo = structdef("UnrealAssetLiveInfo") {
        field("assetPath", string)
        field("assetName", string)
        field("baseClass", string.nullable)
        field("assetClass", string.nullable)
    }
    private val UnrealAssetLiveSearchRequest = structdef("UnrealAssetLiveSearchRequest") {
        field("query", string.nullable)
        field("baseClass", string.nullable)
        field("packagePath", string.nullable)
        field("limit", int).default(200)
    }
    private val UnrealAssetLiveSearchResponse = structdef("UnrealAssetLiveSearchResponse") {
        field("assets", immutableList(UnrealAssetLiveInfo))
    }
    private val UnrealBlueprintInfo = structdef("UnrealBlueprintInfo") {
        field("name", string)
        field("assetPath", string)
    }
    private val UnrealBlueprintHierarchyRequest = structdef("UnrealBlueprintHierarchyRequest") {
        field("baseClass", string)
        field("limit", int).default(1000)
    }
    private val UnrealBlueprintHierarchyResponse = structdef("UnrealBlueprintHierarchyResponse") {
        field("blueprints", immutableList(UnrealBlueprintInfo))
    }
    private val UnrealGameplayTagInfo = structdef("UnrealGameplayTagInfo") {
        field("tagName", string)
        field("assetPath", string)
    }
    private val UnrealGameplayTagsRequest = structdef("UnrealGameplayTagsRequest") {
        field("prefix", string.nullable)
        field("limit", int).default(500)
    }
    private val UnrealGameplayTagsResponse = structdef("UnrealGameplayTagsResponse") {
        field("tags", immutableList(UnrealGameplayTagInfo))
    }
    private val UnrealAssetPropertyInfo = structdef("UnrealAssetPropertyInfo") {
        field("name", string)
        field("typeName", string)
        field("value", string)
    }
    private val UnrealAssetPropertiesRequest = structdef("UnrealAssetPropertiesRequest") {
        field("assetPath", string)
    }
    private val UnrealAssetPropertiesResponse = structdef("UnrealAssetPropertiesResponse") {
        field("objectName", string.nullable)
        field("properties", immutableList(UnrealAssetPropertyInfo))
    }
    private val UnrealDefaultOverrideInfo = structdef("UnrealDefaultOverrideInfo") {
        field("assetPath", string)
        field("instanceName", string)
        field("typeName", string)
        field("value", string)
    }
    private val UnrealDefaultOverridesRequest = structdef("UnrealDefaultOverridesRequest") {
        field("className", string)
        field("fieldName", string)
        field("limit", int).default(200)
    }
    private val UnrealDefaultOverridesResponse = structdef("UnrealDefaultOverridesResponse") {
        field("overrides", immutableList(UnrealDefaultOverrideInfo))
    }

    init {
        property("editorId", 0).readonly.async

        signal("unrealLog", UE4Library.UnrealLogEvent)

        call("filterLinkCandidates", immutableList(LinkRequest), array(ILinkResponse)).async
        call("isMethodReference", MethodReference, bool).async

        signal("navigateToMethod", MethodReference)
        signal("navigateToClass", UE4Library.UClass)

        signal("openBlueprint", UE4Library.BlueprintReference)

        callback("AllowSetForegroundWindow", int, bool)

        property("isConnectedToUnrealEditor", false).readonly.async
        property("isUnrealEngineSolution", false)
        property("isPreBuiltEngine", false)
        property("connectionInfo", UE4Library.ConnectionInfo).readonly

        sink("onEditorPluginOutOfSync", EditorPluginOutOfSync)
        source("installEditorPlugin", InstallPluginDescription)
        source("refreshProjects", void)
        source("enableAutoupdatePlugin", void)

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
        // that need PlayNetMode / RunUnderOneProcess.
        sink("PlaySettingsFromEditor", UE4Library.PlaySettings)
        source("PlaySettingsFromRider", UE4Library.PlaySettings)

        sink("RiderLinkInstallPanelInit", void)
        sink("RiderLinkInstallMessage", InstallMessage).async
        sink("InstallPluginFinished", bool).async
        property("RiderLinkInstallationInProgress", false)
        property("RefreshInProgress", false)
        property("IsUproject", false)
        property("isInstallInfoAvailable", false)
        sink("gamePluginInstallInfos", immutableList(GamePluginInstallInfo))

        source("CancelRiderLinkInstall", void)

        // Hot Reload here is not Unreal's HotReload but generic Hot Reload mechanism which can be either Unreal's HotReload or Unreal's LiveCoding
        property("IsHotReloadAvailable", false).readonly
        property("IsHotReloadCompiling", false).readonly
        signal("TriggerHotReload", void)
        signal("DeletePlugin", void)

        // Python execution — Rider→UE direction; C# backend forwards to RdEditorModel
        call("executeScript", UE4Library.ScriptRequest, UE4Library.ScriptResult).async
        call("executeBatchScripts", UE4Library.BatchScriptRequest, UE4Library.BatchScriptResult).async

        // Asset index queries — Rider PSI only, no editor connection required
        call("searchUnrealAssets",    UnrealAssetSearchRequest,        UnrealAssetSearchResponse).async
        // Live asset query — forwards to UE Editor's AssetRegistry; requires a connected editor.
        call("searchUnrealAssetsLive", UnrealAssetLiveSearchRequest,   UnrealAssetLiveSearchResponse).async
        call("getBlueprintHierarchy", UnrealBlueprintHierarchyRequest, UnrealBlueprintHierarchyResponse).async
        call("searchGameplayTags",    UnrealGameplayTagsRequest,       UnrealGameplayTagsResponse).async
        call("getAssetProperties",    UnrealAssetPropertiesRequest,    UnrealAssetPropertiesResponse).async
        call("findDefaultOverrides",  UnrealDefaultOverridesRequest,   UnrealDefaultOverridesResponse).async
    }
}
