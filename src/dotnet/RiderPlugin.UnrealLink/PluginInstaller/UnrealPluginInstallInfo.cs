﻿using System;
using System.Collections.Generic;
using JetBrains.Rider.Model;
using JetBrains.Util;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink.PluginInstaller
{    
    public class UnrealPluginInstallInfo
    {
        public class InstallDescription
        {
            public bool IsPluginAvailable = false;
            public Version PluginVersion = new Version(0, 0, 0, 0);
            public FileSystemPath UnrealPluginRootFolder = FileSystemPath.Empty;
            public FileSystemPath UprojectFilePath = FileSystemPath.Empty;
        }

        public InstallDescription EnginePlugin = new InstallDescription();
        public List<InstallDescription> ProjectPlugins = new List<InstallDescription>();

        public PluginInstallLocation Location;
    }
}