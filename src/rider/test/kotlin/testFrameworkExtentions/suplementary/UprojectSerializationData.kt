package testFrameworkExtentions.suplementary

import kotlinx.serialization.Serializable

@Serializable
data class UprojectModule(val Name: String, val Type: String, val LoadingPhase: String)

@Serializable
data class UprojectData(
    val FileVersion: Int,
    var EngineAssociation: String,
    val Category: String,
    val Description: String,
    var DisableEnginePluginsByDefault: Boolean,
    val Modules: List<UprojectModule>
)