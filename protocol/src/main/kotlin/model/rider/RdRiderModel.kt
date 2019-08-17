package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel
import model.lib.ue4.UE4Library
import model.lib.ue4.UE4Library.FString

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

        signal("unrealLog", UE4Library.UnrealLogMessage)

        call("isBlueprint", FString, bool).async
        signal("navigate", FString)
    }
}