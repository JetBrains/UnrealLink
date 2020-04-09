using System;
using System.IO;
using System.IO.Compression;
using JetBrains.Application.Settings;
using JetBrains.Application.Threading;
using JetBrains.DataFlow;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.DataContext;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.Util;
using JetBrains.Util.Interop;
using JetBrains.Util.Logging;
using RiderPlugin.UnrealLink.PluginInstaller;
using RiderPlugin.UnrealLink.Settings;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent]
    public class UnrealPluginInstaller
    {
        private readonly Lifetime myLifetime;
        private readonly ILogger myLogger;
        private readonly PluginPathsProvider myPathsProvider;
        private readonly IShellLocks myShellLocks;
        private IContextBoundSettingsStoreLive myBoundSettingsStore;
        private UnrealPluginDetector myPluginDetector;
        private const string BACKUP_SUFFIX = ".backup";

        public UnrealPluginInstaller(Lifetime lifetime, ILogger logger, UnrealPluginDetector pluginDetector,
            PluginPathsProvider pathsProvider, ISolution solution, ISettingsStore settingsStore,
        IShellLocks shellLocks)
        {
            myLifetime = lifetime;
            myLogger = logger;
            myPathsProvider = pathsProvider;
            myShellLocks = shellLocks;
            myBoundSettingsStore = settingsStore.BindToContextLive(myLifetime, ContextRange.Smart(solution.ToDataContext()));
            myPluginDetector = pluginDetector;
            myPluginDetector.InstallInfoProperty.Change.Advise_NoAcknowledgement(myLifetime, installInfo =>
            {
                if (!installInfo.HasNew || installInfo.New == null) return;
                myShellLocks.ExecuteOrQueueReadLockEx(myLifetime, "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                    () =>
                    {
                        var unrealPluginInstallInfo = installInfo.New;
                        if (unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable) return;
                
                        if (!myBoundSettingsStore.GetValue((UnrealLinkSettings s) => s.InstallRiderLinkPlugin))
                            return;
                
                        TryInstallPlugin(unrealPluginInstallInfo);
                    });
            });
            BindToInstallationSettingChange();
        }

        private void TryInstallPlugin(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            bool needToRegenerateProjectFiles = false;

            foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
            {
                var pluginDir = installDescription.UnrealPluginRootFolder;
                var upluginFile = UnrealPluginDetector.GetPathToUpluginFile(pluginDir);
                if (upluginFile.ExistsFile)
                {
                    var version = PluginPathsProvider.GetPluginVersion(upluginFile);
                    if (version != null && version >= myPathsProvider.CurrentPluginVersion) continue;
                }

                var backupDir = pluginDir.AddSuffix(BACKUP_SUFFIX);
                try
                {
                    if (pluginDir.ExistsDirectory)
                        pluginDir.Move(backupDir);
                }
                catch (Exception exception)
                {
                    myLogger.Verbose(exception, ExceptionOrigin.Algorithmic,
                        "Couldn't backup original RiderLink plugin folder");
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
                    if (backupDir.ExistsDirectory)
                        backupDir.Move(pluginDir);
                    continue;
                }

                if (backupDir.ExistsDirectory)
                    backupDir.Delete();

                needToRegenerateProjectFiles = true;
            }

            if (needToRegenerateProjectFiles)
                RegenerateProjectFiles(unrealPluginInstallInfo.ProjectPlugins.FirstNotNull()?.UprojectFilePath);
        }

        private void BindToInstallationSettingChange()
        {
            var entry = myBoundSettingsStore.Schema.GetScalarEntry((UnrealLinkSettings s) => s.InstallRiderLinkPlugin);
            myBoundSettingsStore.GetValueProperty<bool>(myLifetime, entry, null).Change.Advise_NoAcknowledgement(myLifetime, args =>
            {
                if (!args.GetNewOrNull()) return;
                myShellLocks.ExecuteOrQueueReadLockEx(myLifetime, "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                    () =>
                    {
                        var unrealPluginInstallInfo = myPluginDetector.InstallInfoProperty.Value;
                        if (unrealPluginInstallInfo != null)
                        {
                            TryInstallPlugin(unrealPluginInstallInfo);
                        }
                    });
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