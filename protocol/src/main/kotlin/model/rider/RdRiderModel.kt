package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.bool
import com.jetbrains.rd.generator.nova.PredefinedType.int
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel
import model.lib.ue4.UE4Library
import model.lib.ue4.UE4Library.BlueprintClass

@Suppress("unused")
object RdRiderModel : Ext(SolutionModel.Solution) {
    init {
        setting(CSharp50Generator.AdditionalUsings) {
            listOf("JetBrains.Unreal.Lib")
        }
    }

    init {
        property("testConnection", int.nullable)
        property("play", bool)

        signal("unrealLog", UE4Library.LogEvent)

        call("filterBluePrintCandidates", immutableList(BlueprintClass), array(bool)).async
        signal("navigateToBlueprintClass", BlueprintClass)
    }
}