package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel
import model.lib.ue4.UE4Library

@Suppress("unused")
object RdRiderModel : Ext(SolutionModel.Solution) {
    private val stringRange = structdef("stringRange") {
        field("left", int)
        field("right", int)
    }

    init {
        setting(CSharp50Generator.AdditionalUsings) {
            listOf("JetBrains.Unreal.Lib")
        }
    }

    init {


        property("testConnection", int.nullable)
        property("play", bool)

        signal("unrealLog", structdef("rdLogMessage") {
            field("message", UE4Library.UnrealLogMessage)
            field("urlRanges", array(stringRange))
            field("blueprintRanges", array(stringRange))
        })
    }
}