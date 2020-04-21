using System;
using System.Diagnostics;
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
using RiderPlugin.UnrealLink.Utils;

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
        private ProcessingQueue myQueue;
        private const string TMP_PREFIX = "UnrealLink";

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
            myBoundSettingsStore =
                settingsStore.BindToContextLive(myLifetime, ContextRange.Smart(solution.ToDataContext()));
            myPluginDetector = pluginDetector;
            myQueue = new ProcessingQueue(myShellLocks, myLifetime);

            myPluginDetector.InstallInfoProperty.Change.Advise_NewNotNull(myLifetime, installInfo =>
            {
                myShellLocks.ExecuteOrQueueReadLockEx(myLifetime,
                    "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                    () =>
                    {
                        var unrealPluginInstallInfo = installInfo.New;
                        if (unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable)
                        {
                            // TODO: add install plugin to Engine
                            myLogger.Info("[UnrealLink]: Plugin is already installed in Engine");
                            return;
                        }

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

                        QueueInstall(unrealPluginInstallInfo);
                    });
            });
            BindToInstallationSettingChange();
            BindToNotificationFixAction();
        }

        private void QueueInstall(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            myQueue.Enqueue(() => InstallPluginIfRequired(unrealPluginInstallInfo) );
        }

        private void InstallPluginIfRequired(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            bool needToRegenerateProjectFiles = false;

            foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
            {
                if (installDescription.PluginVersion == myPathsProvider.CurrentPluginVersion) continue;
                
                myLogger.Info($"[UnrealLink]: Installing plugin for {installDescription.UprojectFilePath}");
                var pluginDir = installDescription.UnrealPluginRootFolder;
                var backupDir = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
                
                try
                {
                    if (pluginDir.ExistsDirectory)
                    {
                        pluginDir.Copy(backupDir);
                        pluginDir.DeleteChildren();
                    }
                    else
                    {
                        pluginDir.CreateDirectory();
                    }
                }
                catch (Exception exception)
                {
                    myLogger.Error(exception, ExceptionOrigin.Algorithmic,
                        "[UnrealLink]: Couldn't backup original RiderLink plugin folder");
                    backupDir.Delete();
                    continue;
                }

                var editorPluginPathFile = myPathsProvider.PathToPackedPlugin;
                var pluginTmpDir = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
                try
                {
                    ZipFile.ExtractToDirectory(editorPluginPathFile.FullPath,
                        pluginTmpDir.FullPath);
                }
                catch (Exception exception)
                {
                    myLogger.Error(exception, ExceptionOrigin.Algorithmic,
                        $"[UnrealLink]: Couldn't extract {editorPluginPathFile} to {pluginTmpDir}");
                    if (backupDir.ExistsDirectory)
                        backupDir.Copy(pluginDir);
                    pluginTmpDir.Delete();
                    backupDir.Delete();
                    continue;
                }

                var upluginFile = UnrealPluginDetector.GetPathToUpluginFile(pluginTmpDir);
                if (!PatchTypeOfUpluginFile(upluginFile, myLogger, myPluginDetector.UnrealVersion))
                {
                    if (backupDir.ExistsDirectory)
                        backupDir.Copy(pluginDir);
                    backupDir.Delete();
                    pluginTmpDir.Delete();
                    continue;
                }

                // TODO: On UE 4.20 (at least) building plugin from cmd is broken.
                // if (!BuildPlugin(upluginFile,
                //     pluginDir.Directory,
                //     installDescription.UprojectFilePath))
                // {
                //     myLogger.Warn($"Failed to build RiderLink for {installDescription.UprojectFilePath.NameWithoutExtension}. Copying source files instead");
                //     pluginTmpDir.Move(pluginDir);
                // }
                try
                {
                    pluginTmpDir.Copy(pluginDir);
                }
                catch (Exception exception)
                {
                    
                    myLogger.Error(exception, ExceptionOrigin.Algorithmic,
                        $"[UnrealLink]: Couldn't copy from {pluginTmpDir} to {pluginDir}");
                }
                
                
                var notification = new NotificationModel("Unreal Editor plugin installed",
                    "<html>Unreal Editor plugin was installed to:<br>" +
                    $"<b>{pluginDir}<b>" +
                    "</html>", true, RdNotificationEntryType.INFO);

                myShellLocks.ExecuteOrQueue(myLifetime, "UnrealLink.InstallPlugin",
                    () => { myNotificationsModel.Notification(notification); });

                backupDir.Delete();
                pluginTmpDir.Delete();
                
                installDescription.PluginVersion = myPathsProvider.CurrentPluginVersion;
                installDescription.IsPluginAvailable = true;

                needToRegenerateProjectFiles = true;
            }

            if (needToRegenerateProjectFiles)
                RegenerateProjectFiles(unrealPluginInstallInfo.ProjectPlugins.FirstNotNull()?.UprojectFilePath);
        }

        private static bool PatchTypeOfUpluginFile(FileSystemPath upluginFile, ILogger logger, Version pluginVersion)
        {
            var jsonText = File.ReadAllText(upluginFile.FullPath);
            try
            {
                var jsonObject = Newtonsoft.Json.JsonConvert.DeserializeObject(jsonText) as JObject;
                var modules = jsonObject["Modules"];
                var pluginType = pluginVersion.Minor >= 24 ? "UncookedOnly" : "Developer";
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
                logger.Error($"[UnrealLink]: Couldn't patch 'Type' field of {upluginFile}", e);
                return false;
            }

            return true;
        }

        private void BindToInstallationSettingChange()
        {
            var entry = myBoundSettingsStore.Schema.GetScalarEntry((UnrealLinkSettings s) => s.InstallRiderLinkPlugin);
            myBoundSettingsStore.GetValueProperty<bool>(myLifetime, entry, null).Change.Advise_When(myLifetime,
                newValue => newValue, args =>
                {
                    myShellLocks.ExecuteOrQueueReadLockEx(myLifetime,
                        "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                        InstallPluginIfInfoAvailable);
                });
        }

        private void InstallPluginIfInfoAvailable()
        {
            var unrealPluginInstallInfo = myPluginDetector.InstallInfoProperty.Value;
            if (unrealPluginInstallInfo != null)
            {
                QueueInstall(unrealPluginInstallInfo);
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
                model.EnableAutoupdatePlugin.AdviseNotNull(myLifetime, unit =>
                {
                    myBoundSettingsStore.SetValue<UnrealLinkSettings, bool>(s => s.InstallRiderLinkPlugin, true);
                });
            });
        }

        private void RegenerateProjectFiles(FileSystemPath uprojectFilePath)
        {
            if (uprojectFilePath.IsNullOrEmpty())
            {
                myLogger.Error($"[UnrealLink]: Failed refresh project files, couldn't find uproject path: {uprojectFilePath}");
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

            myLogger.Error("[UnrealLink]: Couldn't refresh project files");
            var notification = new NotificationModel($"Failed to refresh project files",
                "<html>RiderLink has been successfully installed to the project:<br>" +
                $"<b>{uprojectFilePath.NameWithoutExtension}<b>" +
                "but refresh project action has failed.<br>" +
                "</html>", true, RdNotificationEntryType.WARN);

            myShellLocks.ExecuteOrQueue(myLifetime, "UnrealLink.RefreshProject",
                () => { myNotificationsModel.Notification(notification); });
        }

        private bool GenerateProjectFilesUsingBat(FileSystemPath engineRoot)
        {
            var isProjectUnderEngine = mySolution.SolutionFilePath.Directory == engineRoot;
            if (!isProjectUnderEngine)
            {
                myLogger.Info($"[UnrealLink]: {mySolution.SolutionFilePath} is not in {engineRoot} ");
                return false;
            }

            var generateProjectFilesBat = engineRoot / "GenerateProjectFiles.bat";
            if (!generateProjectFilesBat.ExistsFile)
            {
                myLogger.Info($"[UnrealLink]: {generateProjectFilesBat} is not available");
                return false;
            }

            try
            {
                myLogger.Info($"[UnrealLink]: Regenerating project files: {generateProjectFilesBat}");
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
                myLogger.Error(errorLevelException,
                    $"[UnrealLink]: Failed refresh project files, calling {generateProjectFilesBat} went wrong");
                return false;
            }

            return true;
        }

        private bool RegenerateProjectUsingUVS(FileSystemPath uprojectFilePath, FileSystemPath engineRoot)
        {
            var pathToUnrealVersionSelector =
                engineRoot / "Engine" / "Binaries" / "Win64" / "UnrealVersionSelector.exe";
            if (!pathToUnrealVersionSelector.ExistsFile)
            {
                myLogger.Info($"[UnrealLink]: {pathToUnrealVersionSelector} is not available");
                return false;
            }

            var commandLine = new CommandLineBuilderJet()
                .AppendSwitch("/projectFiles")
                .AppendFileName(uprojectFilePath);

            try
            {
                myLogger.Info($"[UnrealLink]: Regenerating project files: {pathToUnrealVersionSelector} {commandLine}");
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
                myLogger.Error(errorLevelException, $"[UnrealLink]: Failed refresh project files: calling {pathToUnrealVersionSelector} {commandLine}");
                return false;
            }

            return true;
        }

        private bool RegenerateProjectUsingUBT(FileSystemPath uprojectFilePath, FileSystemPath pathToUnrealBuildToolBin,
            FileSystemPath engineRoot)
        {
            bool isInstalledBuild = IsInstalledBuild(engineRoot);

            var commandLine = new CommandLineBuilderJet()
                .AppendSwitch("-ProjectFiles")
                .AppendSwitch($"-project=\"{uprojectFilePath.FullPath}\"")
                .AppendSwitch("-game");
                
            if (isInstalledBuild)
                commandLine.AppendSwitch("-rocket");
            else
                commandLine.AppendSwitch("-engine");
            
            try
            {
                myLogger.Info($"[UnrealLink]: Regenerating project files: {pathToUnrealBuildToolBin} {commandLine}");
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
                myLogger.Error(errorLevelException, $"[UnrealLink]: Failed refresh project files: calling {pathToUnrealBuildToolBin} {commandLine}");
                return false;
            }

            return true;
        }

        private static bool IsInstalledBuild(FileSystemPath engineRoot)
        {
            var installedbuildTxt = engineRoot / "Engine" / "Build" / "InstalledBuild.txt";
            var isInstalledBuild = installedbuildTxt.ExistsFile;
            return isInstalledBuild;
        }

        private bool BuildPlugin(FileSystemPath upluginPath, FileSystemPath outputDir, FileSystemPath uprojectFile)
        {
            //engineRoot\Engine\Build\BatchFiles\RunUAT.bat" BuildPlugin -Plugin="D:\tmp\RiderLink\RiderLink.uplugin" -Package="D:\PROJECTS\UE\FPS_D_TEST\Plugins\Developer\RiderLink" -Rocket
            var engineRoot = UnrealEngineFolderFinder.FindUnrealEngineRoot(uprojectFile);
            var isInstalledBuild = IsInstalledBuild(engineRoot);
            var commandLine = new CommandLineBuilderJet()
                .AppendSwitch("BuildPlugin")
                .AppendSwitch($"-Plugin=\"{upluginPath.FullPath}\"")
                .AppendSwitch($"-Package=\"{outputDir.FullPath}\"");
            if (isInstalledBuild)
                commandLine.AppendSwitch("-Rocket");

            var pathToUat = engineRoot / "Engine" / "Build" / "BatchFiles" / "RunUAT.bat";
            if (!pathToUat.ExistsFile)
            {
                myLogger.Error("[UnrealLink]: Failed build plugin: RunUAT.bat is not available");
                return false;
            }

            try
            {
                var processStartInfo = new ProcessStartInfo()
                {
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    FileName = pathToUat.FullPath,
                    Arguments = commandLine.ToString()
                };
                var process = new Process
                {
                    EnableRaisingEvents = true,
                    StartInfo = processStartInfo,
                    
                };
                process.Start();
                process.WaitForExit(1000*60);
                if (process.ExitCode != 0)
                {
                    myLogger.Error("[UnrealLink]: Failed build plugin: calling RunUAT.bat went wrong");
                    return false;
                }
            }
            catch (Exception exception)
            {
                myLogger.Error(exception, "[UnrealLink]: Failed build plugin: calling RunUAT.bat went wrong");
                return false;
            }

            return true;
        }
    }
}