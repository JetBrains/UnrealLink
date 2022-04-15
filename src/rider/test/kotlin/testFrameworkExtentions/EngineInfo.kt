package testFrameworkExtentions

import com.intellij.util.application
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.UnrealVersion
import com.jetbrains.rider.cpp.unreal.UnrealShellHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.test.protocol.testProtocolHost

class EngineInfo {
    /**
     * Unreal Engine's versions which will be used in tests.
     * Tests generate base on this data. Can be expanded.
     */
    // TODO: Meditate. Maybe it mustn't be hardcoded
    private val testingVersions: Array<UnrealVersion> = arrayOf(
        UnrealVersion(4, 27, 2),
        UnrealVersion(5, 0, 0)
    )

    var needInstallRiderLink: Boolean = false
    var placeToInstallRiderLink: PluginInstallLocation = PluginInstallLocation.Game

    enum class UnrealOpenType { Sln, Uproject }

    var currentEngine: UnrealEngine? = null

    val engineList : Array<UnrealEngine>
        get() = getEngLst()

    private fun getEngLst(): Array<UnrealEngine> {
        var engLst: Array<UnrealEngine>? = null
        application.invokeAndWait {
            engLst = application.testProtocolHost.components.filterIsInstance<UnrealShellHost>().single()
                .model.getListOfEngines.sync(Unit)
        }
        return engLst!!
    }

    val testingEngines: Array<UnrealEngine> = engineList.filter { it.version in testingVersions }.toTypedArray()
}