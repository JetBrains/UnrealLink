package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel
import model.lib.ue4.UE4Library
import model.lib.ue4.UE4Library.BlueprintClass
import model.lib.ue4.UE4Library.BlueprintFunction
import model.lib.ue4.UE4Library.FString
import model.lib.ue4.UE4Library.StringRange

@Suppress("unused")
object RdRiderModel : Ext(SolutionModel.Solution) {
    init {
        setting(CSharp50Generator.AdditionalUsings) {
            listOf("JetBrains.Unreal.Lib")
        }
    }

    val LinkRequest = structdef("LinkRequest") {
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
        field("class", FString)
        field("method", FString)

        const("separator", string, "::")
    }

    init {
        property("testConnection", int.nullable)
        property("play", bool)

        signal("unrealLog", UE4Library.UnrealLogEvent)

        call("filterLinkCandidates", immutableList(LinkRequest), array(ILinkResponse)).async
        call("isMethodReference", MethodReference, bool).async
        signal("navigateToMethod", MethodReference)

        signal("navigateToBlueprintClass", BlueprintClass)
        signal("navigateToBlueprintFunction", BlueprintFunction)
    }
}
