package model.editorPlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.util.syspropertyOrInvalid
import model.lib.ue4.UE4Library
import model.lib.ue4.UE4Library.UnrealLogMessage
import model.lib.ue4.UE4Library.BlueprintHighlighter
import model.lib.ue4.UE4Library.BlueprintStruct
import model.lib.ue4.UE4Library.FString
import java.io.File

@Suppress("unused")
object RdEditorRoot : Root(
        CSharp50Generator(FlowTransform.AsIs, "JetBrains.Platform.Unreal.EditorPluginModel", File(syspropertyOrInvalid("model.out.src.editorPlugin.csharp.dir"))),
        Cpp17Generator(FlowTransform.Reversed, "Jetbrains.EditorPlugin", File(syspropertyOrInvalid("model.out.src.editorPlugin.cpp.dir")))
) {
    init {
        setting(CSharp50Generator.AdditionalUsings) {
            listOf("JetBrains.Unreal.Lib")
        }
        setting(Cpp17Generator.MarshallerHeaders, listOf("UE4TypesMarshallers.h"))
    }
}

object RdEditorModel : Ext(RdEditorRoot) {
    init {

        property("testConnection", int.nullable)
        signal("unrealLog", UnrealLogMessage)
        property("play", bool)

        call("isBlueprint", BlueprintStruct, bool).async
        signal("navigate", BlueprintStruct)
    }
}