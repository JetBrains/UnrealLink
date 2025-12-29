package com.jetbrains.rider.plugins.unreal.test.cases

import com.jetbrains.rider.test.framework.testData.IRiderTestDataMarker
import java.nio.file.Path

@Suppress("unused")
object RiderTestDataMarker : IRiderTestDataMarker {
  override val testDataFromRoot: Path
    get() = Path.of("src/rider/test/testData")

  override val pluginDirectoryInUltimate: Path
    get() = Path.of("dotnet/Plugins/UnrealLink")
}