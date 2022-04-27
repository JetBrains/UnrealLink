package testFrameworkExtentions

import com.intellij.util.application
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.UnrealVersion
import com.jetbrains.rdclient.util.idea.toIOFile
import com.jetbrains.rider.cpp.unreal.UnrealShellHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.test.protocol.testProtocolHost
import java.io.File

class EngineInfo {
    /**
     * Unreal Engine's versions which will be used in tests.
     * Tests generate base on this data. Can be expanded.
     * Only major and minor are important, the patch version is discarded when filtering engines.
     * See `fun UnrealVersion.basicallyEquals`
     */
    // TODO: Meditate. Maybe it mustn't be hardcoded
    private val testingVersions: Array<UnrealVersion> = arrayOf(
        UnrealVersion(4, 27, 2),
        UnrealVersion(5, 0, 1)
    )

    private fun UnrealVersion.basicallyEquals(other: UnrealVersion): Boolean {
        if (this === other) return true
        if (major != other.major) return false
        if (minor != other.minor) return false

        return true
    }

    var needInstallRiderLink: Boolean = false
    var placeToInstallRiderLink: PluginInstallLocation = PluginInstallLocation.Game
    val pathToRiderLinkInEngine: File
        get() = currentEnginePath!!.resolve("Plugins").resolve("Developer")
            .resolve("RiderLink")

    enum class UnrealOpenType { Sln, Uproject }

    var currentEngine: UnrealEngine? = null
    val currentEnginePath: File?
        get() = currentEngine?.path?.toIOFile()

    val engineList: Array<UnrealEngine>
        get() = getEngLst()

    private fun getEngLst(): Array<UnrealEngine> {
        var engLst: Array<UnrealEngine>? = null
        application.invokeAndWait {
            engLst = application.testProtocolHost.components.filterIsInstance<UnrealShellHost>().single()
                .model.getListOfEngines.sync(Unit)
        }
        return engLst!!
    }

    val testingEngines: Array<UnrealEngine> =
        engineList.filter { eng -> testingVersions.any { it.basicallyEquals(eng.version) } }.toTypedArray()
}