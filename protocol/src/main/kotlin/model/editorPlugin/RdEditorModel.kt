package model.editorPlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rd.generator.nova.cpp.Cpp17Generator
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.util.syspropertyOrEmpty
import java.io.File

@Suppress("unused")
object RdEditorModel: Root(
        CSharp50Generator(FlowTransform.AsIs, "JetBrains.Platform.UnrealEngine.Model", File(syspropertyOrEmpty("model.out.src.editorPlugin.csharp.dir"))),
//  CSharp50Generator(FlowTransform.Reversed, "JetBrains.Platform.UnrealEngine.Model", File(syspropertyOrEmpty("model.out.src.unrealengine.dir"))),
        Cpp17Generator(FlowTransform.Reversed, "com_jetbrains_rider_plugins_unrealengine", File(syspropertyOrEmpty("model.out.src.editorPlugin.cpp.dir")))
//        Kotlin11Generator(FlowTransform.AsIs, "com.jetbrains.rider.plugins.unrealengine", File(syspropertyOrEmpty("model.out.src.kt.dir")))
){
    init {

        property("test_connection", int.nullable)
        property("unreal_log", string)
        property("play", bool)
//        property("test_string", string)
    }
}