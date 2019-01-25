package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rider.model.nova.ide.SolutionModel

@Suppress("unused")
object RdRiderModel: Ext(SolutionModel.Solution){
    init {

        property("test_connection", int.nullable)
        property("unreal_log", string)
        property("play", bool)
//        property("test_string", string)
    }
}