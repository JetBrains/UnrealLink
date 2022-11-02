using System.Collections.Generic;
using JetBrains.Util;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink.PluginInstaller
{    
    public class UnrealPluginInstallInfo
    {
        public class InstallDescription
        {
            public bool IsPluginAvailable = false;
            public byte[] PluginChecksum = null;
            public VirtualFileSystemPath UnrealPluginRootFolder = VirtualFileSystemPath.GetEmptyPathFor(InteractionContext.SolutionContext);
            public string ProjectName = string.Empty;
            public VirtualFileSystemPath UprojectPath = VirtualFileSystemPath.GetEmptyPathFor(InteractionContext.SolutionContext);
        }

        public VirtualFileSystemPath EngineRoot = null;

        public InstallDescription EnginePlugin = new();
        public readonly List<InstallDescription> ProjectPlugins = new();

        public PluginInstallLocation Location;
    }
}