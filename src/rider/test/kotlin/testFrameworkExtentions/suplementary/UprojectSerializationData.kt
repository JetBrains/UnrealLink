@file:Suppress("PropertyName")

package testFrameworkExtentions.suplementary

import com.fasterxml.jackson.annotation.JsonProperty

data class UprojectModule(@get:JsonProperty("Name") val Name: String,
                          @get:JsonProperty("Type") val Type: String,
                          @get:JsonProperty("LoadingPhase") val LoadingPhase: String,
                          @get:JsonProperty("AdditionalDependencies") val AdditionalDependencies: List<String>)

data class UprojectPlugins(@get:JsonProperty("Name") val Name: String,
                           @get:JsonProperty("Enabled") val Enabled: Boolean,
                           @get:JsonProperty("TargetAllowList") val TargetAllowList: List<String>)

data class UprojectData(@get:JsonProperty("FileVersion") val FileVersion: Int,
                        @get:JsonProperty("EngineAssociation") var EngineAssociation: String,
                        @get:JsonProperty("Category") val Category: String,
                        @get:JsonProperty("Description") val Description: String,
                        @get:JsonProperty("DisableEnginePluginsByDefault") var DisableEnginePluginsByDefault: Boolean,
                        @get:JsonProperty("Modules") val Modules: List<UprojectModule>,
                        @get:JsonProperty("Plugins") val Plugins: List<UprojectPlugins>)
