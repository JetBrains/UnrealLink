using System;
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
            public String ProjectName = String.Empty;
            public VirtualFileSystemPath UprojectPath = VirtualFileSystemPath.GetEmptyPathFor(InteractionContext.SolutionContext);
        }

        public VirtualFileSystemPath EngineRoot = null;

        public InstallDescription EnginePlugin = new InstallDescription();
        public List<InstallDescription> ProjectPlugins = new List<InstallDescription>();

        public PluginInstallLocation Location;
    }
}