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

    val LinkResponseUnresolved = structdef("LinkResponseUnresolved") extends  ILinkResponse {}

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
        field("installedVersion", string)
        field("requiredVersion", string)
        field("status", PluginInstallStatus)
    }

    private val PluginInstallLocation = enum("PluginInstallLocation") {
        +"Engine"
        +"Game"
        +"NotInstalled"
    }

    init {
        property("editorId", 0).readonly.async
        property("playMode", int)
        source("frameSkip", void)

        signal("unrealLog", UE4Library.UnrealLogEvent)

        call("filterLinkCandidates", immutableList(LinkRequest), array(ILinkResponse)).async
        call("isMethodReference", MethodReference, bool).async

        signal("navigateToMethod", MethodReference)
        signal("navigateToClass", UE4Library.UClass)

        signal("openBlueprint", UE4Library.BlueprintReference)

        callback("AllowSetForegroundWindow", int, bool)

        property("isConnectedToUnrealEditor", false).readonly.async
        property("isUnrealEngineSolution", false)

        sink("onEditorPluginOutOfSync", EditorPluginOutOfSync)
        source("installEditorPlugin", PluginInstallLocation)
        source("enableAutoupdatePlugin", void)

        sink("PlayStateFromEditor", UE4Library.PlayState)
        source("PlayStateFromRider", UE4Library.PlayState)
    }
}
