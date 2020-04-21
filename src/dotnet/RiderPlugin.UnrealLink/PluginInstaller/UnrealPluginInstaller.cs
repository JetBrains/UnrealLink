using System;
using System.IO;
using System.IO.Compression;
using JetBrains.Application.Settings;
using JetBrains.Application.Threading;
using JetBrains.Collections.Viewable;
using JetBrains.DataFlow;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.DataContext;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.Rider.Model.Notifications;
using JetBrains.Util;
using JetBrains.Util.Interop;
using Newtonsoft.Json.Linq;
using RiderPlugin.UnrealLink.Settings;

namespace RiderPlugin.UnrealLink.PluginInstaller
{
    [SolutionComponent]
    public class UnrealPluginInstaller
    {
        private readonly Lifetime myLifetime;
        private readonly ILogger myLogger;
        private readonly PluginPathsProvider myPathsProvider;
        private readonly ISolution mySolution;
        private readonly IShellLocks myShellLocks;
        private readonly UnrealHost myUnrealHost;
        private readonly NotificationsModel myNotificationsModel;
        private IContextBoundSettingsStoreLive myBoundSettingsStore;
        private UnrealPluginDetector myPluginDetector;
        private const string BACKUP_SUFFIX = ".backup";

        public UnrealPluginInstaller(Lifetime lifetime, ILogger logger, UnrealPluginDetector pluginDetector,
            PluginPathsProvider pathsProvider, ISolution solution, ISettingsStore settingsStore,
        IShellLocks shellLocks, UnrealHost unrealHost, NotificationsModel notificationsModel)
        {
            myLifetime = lifetime;
            myLogger = logger;
            myPathsProvider = pathsProvider;
            mySolution = solution;
            myShellLocks = shellLocks;
            myUnrealHost = unrealHost;
            myNotificationsModel = notificationsModel;
            myBoundSettingsStore = settingsStore.BindToContextLive(myLifetime, ContextRange.Smart(solution.ToDataContext()));
            myPluginDetector = pluginDetector;
            
            myPluginDetector.InstallInfoProperty.Change.Advise_NewNotNull(myLifetime, installInfo =>
            {
                myShellLocks.ExecuteOrQueueReadLockEx(myLifetime, "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                    () =>
                    {
                        var unrealPluginInstallInfo = installInfo.New;
                        if (unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable)
                        {
                            // TODO: add install plugin to Engine
                            return;
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
                myLogger.Warn($"Couldn't patch 'Type' field of {upluginFile}", e);
            }
        }

        private void BindToInstallationSettingChange()
        {
            var entry = myBoundSettingsStore.Schema.GetScalarEntry((UnrealLinkSettings s) => s.InstallRiderLinkPlugin);
            myBoundSettingsStore.GetValueProperty<bool>(myLifetime, entry, null).Change.Advise_When(myLifetime, newValue => newValue, args =>
            {
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
            if (uprojectFilePath.IsNullOrEmpty())
            {
                myLogger.Error($"Failed refresh project files, couldn't find uproject path: {uprojectFilePath}");
                return;
            }
            
            var engineRoot = UnrealEngineFolderFinder.FindUnrealEngineRoot(uprojectFilePath);
            var pathToUnrealBuildToolBin = UnrealEngineFolderFinder.GetAbsolutePathToUnrealBuildToolBin(engineRoot);

            // 1. If project is under engine root, use GenerateProjectFiles.bat first
            if (GenerateProjectFilesUsingBat(engineRoot)) return;
            // 2. If it's a standalone project, use UnrealVersionSelector
            //    The same way "Generate project files" from context menu of .uproject works
            if (RegenerateProjectUsingUVS(uprojectFilePath, engineRoot)) return;
            // 3. If UVS is missing or have failed, fallback to UnrealBuildTool
            if (RegenerateProjectUsingUBT(uprojectFilePath, pathToUnrealBuildToolBin, engineRoot)) return;
            
            myLogger.Error("Couldn't refresh project files");
            var notification = new NotificationModel($"Failed to refresh project files", "<html>RiderLink has been successfully installed to the project:<br>" +
                                                                                         "<b>{uprojectFilePath.ExtensionNoDot}<b>" +
                                                                                         "but refresh project action has failed.<br>" +
                                                                                         "</html>", true, RdNotificationEntryType.WARN);
            
            myShellLocks.ExecuteOrQueue(myLifetime, "UnrealLink.RefreshProject", () =>
            {
                myNotificationsModel.Notification(notification);
            });
        }

        private bool GenerateProjectFilesUsingBat(FileSystemPath engineRoot)
        { 
            var isProjectUnderEngine = mySolution.SolutionFilePath.Directory == engineRoot;
            if (!isProjectUnderEngine) return false;
            
            var generateProjectFilesBat = engineRoot / "GenerateProjectFiles.bat";
            if (!generateProjectFilesBat.ExistsFile) return false;
            
            try
            {
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    generateProjectFilesBat,
                    null,
                    LoggingLevel.INFO,
                    TimeSpan.FromMinutes(1),
                    InvokeChildProcess.TreatStderr.AsOutput,
                    generateProjectFilesBat.Directory
                ));
            }
            catch (ErrorLevelException errorLevelException)
            {
                myLogger.Error(errorLevelException, $"Failed refresh project files, calling {generateProjectFilesBat} went wrong");
                return false;
            }

            return true;
        }

        private bool RegenerateProjectUsingUVS(FileSystemPath uprojectFilePath, FileSystemPath engineRoot)
        {
            var pathToUnrealVersionSelector = engineRoot / "Engine"/ "Binaries" / "Win64" / "UnrealVersionSelector.exe";
            if (!pathToUnrealVersionSelector.ExistsFile) return false;
            
            var commandLine = new CommandLineBuilderJet()
                .AppendSwitch("/projectFiles")
                .AppendFileName(uprojectFilePath);

            try
            {
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    pathToUnrealVersionSelector,
                    commandLine,
                    LoggingLevel.INFO,
                    TimeSpan.FromMinutes(1),
                    InvokeChildProcess.TreatStderr.AsOutput,
                    pathToUnrealVersionSelector.Directory
                ));
            }
            catch (ErrorLevelException errorLevelException)
            {
                myLogger.Error(errorLevelException, "Failed refresh project files, calling UVS went wrong");
                return false;
            }

            return true;
        }

        private bool RegenerateProjectUsingUBT(FileSystemPath uprojectFilePath, FileSystemPath pathToUnrealBuildToolBin,
            FileSystemPath engineRoot)
        {
            var installedbuildTxt = engineRoot / "Engine" / "Build" / "InstalledBuild.txt";
            var isInstalledBuild = installedbuildTxt.ExistsFile;
            
            var commandLine = new CommandLineBuilderJet()
                .AppendSwitch("-ProjectFiles")
                .AppendFileName(uprojectFilePath)
                .AppendSwitch("-game")
                .AppendSwitch("-engine");
            if (isInstalledBuild)
                commandLine.AppendSwitch("-rocket");

            try
            {
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    pathToUnrealBuildToolBin,
                    commandLine,
                    LoggingLevel.INFO,
                    TimeSpan.FromMinutes(1),
                    InvokeChildProcess.TreatStderr.AsOutput,
                    pathToUnrealBuildToolBin.Directory
                ));
            }
            catch (ErrorLevelException errorLevelException)
            {
                myLogger.Error(errorLevelException, "Failed refresh project files, calling UBT went wrong");
                return false;
            }

            return true;
        }
    }
}