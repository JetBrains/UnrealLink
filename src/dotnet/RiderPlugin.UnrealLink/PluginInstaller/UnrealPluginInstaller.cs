using System;
using System.IO;
using System.IO.Compression;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.Util;
using JetBrains.Util.Interop;
using JetBrains.Util.Logging;
using RiderPlugin.UnrealLink.PluginInstaller;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent]
    public class UnrealPluginInstaller
    {
        private readonly Lifetime myLifetime;
        private readonly ILogger myLogger;
        private readonly PluginPathsProvider myPathsProvider;
        private Version currentVersion;
        private const string BACKUP_SUFFIX = ".backup";

        public UnrealPluginInstaller(Lifetime lifetime, ILogger logger, UnrealPluginDetector pluginDetector,
            PluginPathsProvider pathsProvider)
        {
            myLifetime = lifetime;
            myLogger = logger;
            myPathsProvider = pathsProvider;
            pluginDetector.InstallInfoProperty.Change.Advise(myLifetime, installInfo =>
            {
                if (!installInfo.HasNew || installInfo.New == null) return;
                
                var unrealPluginInstallInfo = installInfo.New;
                if (unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable) return;

                bool needToRegenerateProjectFiles = false;

                foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
                {
                    var pluginDir = installDescription.UnrealPluginRootFolder;
                    var upluginFile = UnrealPluginDetector.GetPathToUpluginFile(pluginDir);
                    if (upluginFile.ExistsFile)
                    {
                        var version = PluginPathsProvider.GetPluginVersion(upluginFile);
                        if(version != null && version >= myPathsProvider.CurrentPluginVersion) continue;
                    }
                    var backupDir = pluginDir.AddSuffix(BACKUP_SUFFIX);
                    try
                    {
                        if(pluginDir.ExistsDirectory)
                            pluginDir.Move(backupDir);
                    }
                    catch (Exception exception)
                    {
                        myLogger.Verbose(exception, ExceptionOrigin.Algorithmic, "Couldn't backup original RiderLink plugin folder");
                        continue;
                    }

                    var editorPluginPathFile = myPathsProvider.PathToPackedPlugin;

                    try
                    {
                        ZipFile.ExtractToDirectory(editorPluginPathFile.FullPath,
                            pluginDir.FullPath);
                    }
                    catch (Exception _)
                    {
                        backupDir.Move(pluginDir);
                        continue;
                    }

                    backupDir.Delete();

                    needToRegenerateProjectFiles = true;                    
                }
                if(needToRegenerateProjectFiles)
                    RegenerateProjectFiles(unrealPluginInstallInfo.ProjectPlugins.FirstNotNull()?.UprojectFilePath);
            });
        }

        private void RegenerateProjectFiles(FileSystemPath uprojectFilePath)
        {
            if (uprojectFilePath == null || uprojectFilePath.IsEmpty) return;
            // {UnrealEngineRoot}/Engine/Binaries/DotNET/UnrealBuildTool.exe  -projectfiles {uprojectFilePath} -game -engine
            var engineRoot = UnrealEngineFolderFinder.FindUnrealEngineRoot(uprojectFilePath);
            var pathToUnrealBuildToolBin = UnrealEngineFolderFinder.GetAbsolutePathToUnrealBuildToolBin(engineRoot);
            var commandLine = new CommandLineBuilderJet()
                .AppendSwitch("-ProjectFiles")
                .AppendFileName(uprojectFilePath)
                .AppendSwitch("-game")
                .AppendSwitch("-engine");

            try
            {
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    pathToUnrealBuildToolBin,
                    commandLine,
                    LoggingLevel.INFO,
                    TimeSpan.FromMinutes(10),
                    InvokeChildProcess.TreatStderr.AsOutput,
                    pathToUnrealBuildToolBin.Directory
                ));
            }
            catch (ErrorLevelException)
            {
                // TODO: handle properly
            }
        }
    }
}