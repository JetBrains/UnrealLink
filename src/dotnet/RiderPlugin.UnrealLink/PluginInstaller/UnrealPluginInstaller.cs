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
using Newtonsoft.Json.Linq;
using JetBrains.Collections.Viewable;
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
        private readonly UnrealHost myUnrealHost;
        private IContextBoundSettingsStoreLive myBoundSettingsStore;
        private UnrealPluginDetector myPluginDetector;
        private const string BACKUP_SUFFIX = ".backup";

        public UnrealPluginInstaller(Lifetime lifetime, ILogger logger, UnrealPluginDetector pluginDetector,
            PluginPathsProvider pathsProvider, ISolution solution, ISettingsStore settingsStore,
        IShellLocks shellLocks, UnrealHost unrealHost)
        {
            myLifetime = lifetime;
            myLogger = logger;
            myPathsProvider = pathsProvider;
            myShellLocks = shellLocks;
            myUnrealHost = unrealHost;
            myBoundSettingsStore = settingsStore.BindToContextLive(myLifetime, ContextRange.Smart(solution.ToDataContext()));
            myPluginDetector = pluginDetector;
            
            myPluginDetector.InstallInfoProperty.Change.Advise_NoAcknowledgement(myLifetime, installInfo =>
            {
                if (!installInfo.HasNew || installInfo.New == null) return;
                myShellLocks.ExecuteOrQueueReadLockEx(myLifetime, "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                    () =>
                    {
                        var unrealPluginInstallInfo = installInfo.New;
                        if (unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable)
                        {
                            // TODO: add install plugin to Engine
                        };

                        if (!myBoundSettingsStore.GetValue((UnrealLinkSettings s) => s.InstallRiderLinkPlugin))
                        {
                            foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
                            {
                                if (installDescription.IsPluginAvailable == false ||
                                    installDescription.PluginVersion != myPathsProvider.CurrentPluginVersion)
                                {
                                    myUnrealHost.PerformModelAction(model => model.OnEditorModelOutOfSync());
                                }
                            }

                            return;
                        }
                
                        InstallPluginIfRequired(unrealPluginInstallInfo);
                    });
            });
            BindToInstallationSettingChange();
            BindToNotificationFixAction();
        }

        private void InstallPluginIfRequired(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            bool needToRegenerateProjectFiles = false;

            foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
            {
                if (installDescription.PluginVersion == myPathsProvider.CurrentPluginVersion) continue;
                
                var pluginDir = installDescription.UnrealPluginRootFolder;
                var upluginFile = UnrealPluginDetector.GetPathToUpluginFile(pluginDir);
                

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

                PatchTypeOfUpluginFile(upluginFile);

                needToRegenerateProjectFiles = true;
            }

            if (needToRegenerateProjectFiles)
                RegenerateProjectFiles(unrealPluginInstallInfo.ProjectPlugins.FirstNotNull()?.UprojectFilePath);
        }

        private void PatchTypeOfUpluginFile(FileSystemPath upluginFile)
        {
            var jsonText = File.ReadAllText(upluginFile.FullPath);
            try
            {
                var jsonObject = Newtonsoft.Json.JsonConvert.DeserializeObject(jsonText) as JObject;
                var modules = jsonObject["Modules"];
                var pluginType = myPluginDetector.UnrealVersion.Minor >= 24 ? "UncookedOnly" : "Developer";
                if (modules is JArray array)
                {
                    foreach (var item in array)
                    {
                        item["Type"].Replace(pluginType);
                    }
                }

                File.WriteAllText(upluginFile.FullPath, jsonObject.ToString());
            }
            catch (Exception e)
            {
                myLogger.Log(LoggingLevel.WARN, $@"Couldn't patch 'Type' field of {upluginFile}", e);
            }
        }

        private void BindToInstallationSettingChange()
        {
            var entry = myBoundSettingsStore.Schema.GetScalarEntry((UnrealLinkSettings s) => s.InstallRiderLinkPlugin);
            myBoundSettingsStore.GetValueProperty<bool>(myLifetime, entry, null).Change.Advise_NoAcknowledgement(myLifetime, args =>
            {
                if (!args.GetNewOrNull()) return;
                myShellLocks.ExecuteOrQueueReadLockEx(myLifetime, "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                    InstallPluginIfInfoAvailable);
            });
        }

        private void InstallPluginIfInfoAvailable()
        {
            var unrealPluginInstallInfo = myPluginDetector.InstallInfoProperty.Value;
            if (unrealPluginInstallInfo != null)
            {
                InstallPluginIfRequired(unrealPluginInstallInfo);
            }
        }

        private void BindToNotificationFixAction()
        {
            myUnrealHost.PerformModelAction(model =>
            {
                model.InstallEditorPlugin.AdviseNotNull(myLifetime, unit =>
                {
                    myShellLocks.ExecuteOrQueueReadLockEx(myLifetime,
                        "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                        InstallPluginIfInfoAvailable);
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