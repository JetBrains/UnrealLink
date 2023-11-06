package testFrameworkExtentions

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rdclient.util.idea.toIOFile
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import java.io.File


class EngineInfo {
  var needInstallRiderLink: Boolean = false
  var placeToInstallRiderLink: PluginInstallLocation = PluginInstallLocation.Game
  val pathToRiderLinkInEngine: File
    get() = currentEnginePath!!.resolve("Plugins").resolve("Developer")
      .resolve("RiderLink")

  var currentEngine: UnrealEngine? = null
  val currentEnginePath: File?
    get() = currentEngine?.path?.toIOFile()
}