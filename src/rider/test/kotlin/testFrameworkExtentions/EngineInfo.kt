package testFrameworkExtentions

import com.intellij.util.application
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.UnrealVersion
import com.jetbrains.rdclient.protocol.getComponent
import com.jetbrains.rdclient.util.idea.toIOFile
import com.jetbrains.rider.cpp.unreal.UnrealShellHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.test.protocol.testProtocolHost
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.Duration

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
        UnrealVersion(5, 0, 3),
        UnrealVersion(5, 1, 0),
        UnrealVersion(5, 2, 0)
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

    val installedEngineList: Array<UnrealEngine> = runBlocking {
        withTimeout(Duration.ofSeconds(30).toMillis()) {
            val model = application.testProtocolHost.getComponent<UnrealShellHost>().model
            model.getListOfEngines.startSuspending(Unit)
        }
    }

    val testingEngines: Array<UnrealEngine> =
        installedEngineList.filter { eng -> testingVersions.any { it.basicallyEquals(eng.version) } }.toTypedArray()
}