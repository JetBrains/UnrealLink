@file:Suppress("JUnitTestCaseWithNoTests")

package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.unreal.UnrealTestLevelProject
import org.testng.annotations.AfterMethod
import java.time.Duration

open class UnrealLinkBase: UnrealTestLevelProject() {
  @AfterMethod
  override fun unrealCleanup() {
    deleteRiderLink()
    super.unrealCleanup()
  }
  
  protected fun installRiderLink(place: PluginInstallLocation, timeout: Duration = Duration.ofSeconds(240)) {
    logger.info("Installing RiderLink in ${place.name}")
    var riderLinkInstalled = false
    project.solution.rdRiderModel.installPluginFinished.advise(Lifetime.Eternal) { riderLinkInstalled = true }
    project.solution.rdRiderModel.installEditorPlugin.fire(
      InstallPluginDescription(place, ForceInstall.Yes)
    )
    waitAndPump(timeout, { riderLinkInstalled }, { "RiderLink has not been installed" })
  }

  protected fun deleteRiderLink() {
    project.solution.rdRiderModel.deletePlugin.fire()
    waitPumping(Duration.ofSeconds(5))
  }
}