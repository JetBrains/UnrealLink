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
        field("IsEngineAvailable", bool)
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

        sink("RiderLinkInstallPanelInit", void)
        sink("RiderLinkInstallMessage", InstallMessage).async
        sink("InstallPluginFinished", bool).async
        property("RiderLinkInstallationInProgress", false)
        property("RefreshInProgress", false)
        property("IsUproject", false)
        property("isInstallInfoAvailable", false)

        source("CancelRiderLinkInstall", void)
    }
}
