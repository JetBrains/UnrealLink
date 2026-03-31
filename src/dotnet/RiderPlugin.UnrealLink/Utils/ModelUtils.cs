using System.Collections.Generic;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink.Utils;

public static class ModelUtils
{
    public static InstallPluginDescription CreateInstallPluginDescription(PluginInstallLocation installLocation, ForceInstall forceInstall, bool shouldBeBuilt = true)
    {
        return new InstallPluginDescription(installLocation, forceInstall, shouldBeBuilt, [], []);
    }
        
    public static InstallPluginDescription CreateInstallPluginDescription(PluginInstallLocation installLocation, ForceInstall forceInstall, bool shouldBeBuilt, List<string> selectedUprojectPaths, List<string> unselectedUprojectPaths)
    {
        return new InstallPluginDescription(installLocation, forceInstall, shouldBeBuilt, selectedUprojectPaths, unselectedUprojectPaths);
    }
}