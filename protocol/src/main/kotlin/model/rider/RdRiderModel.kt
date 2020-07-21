package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel
import com.jetbrains.rider.model.nova.ide.UiContextModel
import model.lib.ue4.UE4Library
import model.lib.ue4.UE4Library.UClass
import model.lib.ue4.UE4Library.BlueprintReference
import model.lib.ue4.UE4Library.FString
import model.lib.ue4.UE4Library.StringRange

@Suppress("unused")
object RdRiderModel : Ext(SolutionModel.Solution) {
    init {
        setting(CSharp50Generator.AdditionalUsings) {
            listOf("JetBrains.Unreal.Lib")
        }
    }

    private val LinkRequest = structdef("LinkRequest") {
        field("data", FString)
    }

    val ILinkResponse = basestruct("ILinkResponse") {}

    val LinkResponseBlueprint = structdef("LinkResponseBlueprint") extends ILinkResponse {
        field("fullPath", FString)
        field("range", StringRange)
    }

    val LinkResponseFilePath = structdef("LinkResponseFilePath") extends ILinkResponse {
        field("fullPath", FString)
        field("range", StringRange)
    }

    val LinkResponseUnresolved = structdef("LinkResponseUnresolved") extends  ILinkResponse {}

    private val MethodReference = structdef("MethodReference") {
        field("class", UClass)
        field("method", FString)

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
        signal("navigateToClass", UClass)

        signal("openBlueprint", BlueprintReference)

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
