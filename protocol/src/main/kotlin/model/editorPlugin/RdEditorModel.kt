package model.editorPlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.bool
import com.jetbrains.rd.generator.nova.PredefinedType.int
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.util.syspropertyOrInvalid
import model.lib.ue4.UE4Library
import model.lib.ue4.UE4Library.UClass
import model.lib.ue4.UE4Library.FString
import model.lib.ue4.UE4Library.UnrealLogEvent
import java.io.File

@Suppress("unused")
object RdEditorRoot : Root(
        CSharp50Generator(FlowTransform.AsIs, "JetBrains.Platform.Unreal.EditorPluginModel", File(syspropertyOrInvalid("model.out.src.editorPlugin.csharp.dir"))),
        Cpp17Generator(FlowTransform.Reversed, "Jetbrains::EditorPlugin", File(syspropertyOrInvalid("model.out.src.editorPlugin.cpp.dir")))
) {
    init {
        setting(CSharp50Generator.AdditionalUsings) {
            listOf("JetBrains.Unreal.Lib")
        }
        setting(Cpp17Generator.AdditionalHeaders, listOf("UE4TypesMarshallers.h"))
    }
}

object RdEditorModel : Ext(RdEditorRoot) {
    init {
        signal("unrealLog", UnrealLogEvent).async
        property("play", bool)

        signal("openBlueprint", UE4Library.BlueprintReference)

        signal("onBlueprintAdded", UClass).async
        call("isBlueprintPathName", FString, bool)
        call("getPathNameByPath", FString, FString.nullable)

        callback("AllowSetForegroundWindow", int, bool)
    }
}
