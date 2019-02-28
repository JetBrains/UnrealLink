package model.editorPlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.util.syspropertyOrEmpty
import java.io.File

@Suppress("unused")
object RdEditorModel: Root(
        CSharp50Generator(FlowTransform.AsIs, "JetBrains.Platform.UnrealEngine.Model", File(syspropertyOrEmpty("model.out.src.editorPlugin.csharp.dir"))),
        Cpp17Generator(FlowTransform.Reversed, "com_jetbrains_rider_plugins_unrealengine", File(syspropertyOrEmpty("model.out.src.editorPlugin.cpp.dir")))
){
    init {

        property("test_connection", int.nullable)
        signal("unreal_log", string)
        property("play", bool)
    }
}