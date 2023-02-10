package model.editorPlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
@Suppress("unused")
object LiveCodingModel : Ext(RdEditorModel) {
        init {
                call("LC_IsEnabledByDefault", void, bool)
                source("LC_EnableByDefault", bool)

                call("LC_IsEnabledForSession", void, bool)
                call("LC_CanEnableForSession", void, bool)
                source("LC_EnableForSession", bool)

                call("LC_IsCompiling", void, bool)
                call("LC_HasStarted", void, bool)
                source("LC_Compile", void)
                sink("LC_OnPatchComplete", void)
        }
}
