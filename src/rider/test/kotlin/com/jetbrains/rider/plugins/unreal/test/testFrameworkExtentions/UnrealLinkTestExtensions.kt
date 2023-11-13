package com.jetbrains.rider.plugins.unreal.test.testFrameworkExtentions

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.ForceInstall
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.InstallPluginDescription
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.PluginInstallLocation
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.unreal.UnrealBase
import java.time.Duration

private var myNeedInstallRiderLink: Boolean = false

var UnrealBase.needInstallRiderLink: Boolean
  get() {
    return myNeedInstallRiderLink
  }
  set(value) {
    myNeedInstallRiderLink = value
  }

private var myPlaceToInstallRiderLink = PluginInstallLocation.Game

var UnrealBase.placeToInstallRiderLink: PluginInstallLocation
  get() = myPlaceToInstallRiderLink
  set(value) {
    myPlaceToInstallRiderLink = value
  }

fun UnrealBase.installRiderLink(place: PluginInstallLocation, timeout: Duration = Duration.ofSeconds(240)) {
  var riderLinkInstalled = false
  project.solution.rdRiderModel.installPluginFinished.advise(Lifetime.Eternal) { riderLinkInstalled = true }
  project.solution.rdRiderModel.installEditorPlugin.fire(
    InstallPluginDescription(place, ForceInstall.Yes)
  )
  waitAndPump(timeout, { riderLinkInstalled }, { "RiderLink has not been installed" })
}
