using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using JetBrains.Application.Settings;
using JetBrains.Application.Threading;
using JetBrains.Collections.Viewable;
using JetBrains.DataFlow;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.DataContext;
using JetBrains.ReSharper.Feature.Services.Cpp.ProjectModel.UE4;
using JetBrains.ReSharper.Host.Features.BackgroundTasks;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Model;
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
        public Lifetime Lifetime { get; private set; }
        private readonly ILogger myLogger;
        private readonly PluginPathsProvider myPathsProvider;
        private readonly ISolution mySolution;
        private readonly UnrealHost myUnrealHost;
        private readonly NotificationsModel myNotificationsModel;
        private readonly RiderBackgroundTaskHost myBackgroundTaskHost;
        private IContextBoundSettingsStoreLive myBoundSettingsStore;
        private UnrealPluginDetector myPluginDetector;
        private const string TMP_PREFIX = "UnrealLink";
        
        public IProperty<bool> InstallationIsInProgress { get; private set; }

        public UnrealPluginInstaller(Lifetime lifetime, ILogger logger, UnrealPluginDetector pluginDetector,
            PluginPathsProvider pathsProvider, ISolution solution, ISettingsStore settingsStore, UnrealHost unrealHost,
            NotificationsModel notificationsModel, RiderBackgroundTaskHost backgroundTaskHost)
        {
            InstallationIsInProgress = new Property<bool>(lifetime, "UnrealLink.InstallationIsInProgress", false);
            Lifetime = lifetime;
            myLogger = logger;
            myPathsProvider = pathsProvider;
            mySolution = solution;
            myUnrealHost = unrealHost;
            myNotificationsModel = notificationsModel;
            myBackgroundTaskHost = backgroundTaskHost;
            myBoundSettingsStore =
                settingsStore.BindToContextLive(Lifetime, ContextRange.Smart(solution.ToDataContext()));
            myPluginDetector = pluginDetector;

            myPluginDetector.InstallInfoProperty.Change.Advise_NewNotNull(Lifetime, installInfo =>
            {
                mySolution.Locks.ExecuteOrQueueReadLockEx(Lifetime,
                    "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                    () =>
                    {
                        HandleAutoUpdatePlugin(installInfo.New);
                    });
            });
            BindToInstallationSettingChange();
            BindToNotificationFixAction();
        }

        private void HandleAutoUpdatePlugin(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            var status = PluginInstallStatus.NoPlugin;
            var outOfSync = true;
            Version installedVersion = new Version();
            if (unrealPluginInstallInfo.Location == PluginInstallLocation.Engine)
            {
                status = PluginInstallStatus.InEngine;
                outOfSync = unrealPluginInstallInfo.EnginePlugin.PluginVersion !=
                            myPathsProvider.CurrentPluginVersion;
                installedVersion = unrealPluginInstallInfo.EnginePlugin.PluginVersion;
            }

            if (unrealPluginInstallInfo.Location == PluginInstallLocation.Game)
            {
                status = PluginInstallStatus.InGame;
                outOfSync = unrealPluginInstallInfo.ProjectPlugins.Any(description =>
                {
                    var isNotSynced = description.PluginVersion != myPathsProvider.CurrentPluginVersion;
                    if(isNotSynced)
                        installedVersion = description.PluginVersion;
                    return isNotSynced;
                });
            }

            if (!myBoundSettingsStore.GetValue((UnrealLinkSettings s) => s.InstallRiderLinkPlugin) ||
                status == PluginInstallStatus.NoPlugin)
            {
                if (outOfSync)
                {
                    myLogger.Warn("[UnrealLink]: Plugin is out of sync");
                    myUnrealHost.PerformModelAction(model => model.OnEditorPluginOutOfSync(new EditorPluginOutOfSync(
                        installedVersion.ToString(), myPathsProvider.CurrentPluginVersion.ToString(), status)));
                }

                return;
            }

            QueueAutoUpdate(unrealPluginInstallInfo);
        }

        private void QueueAutoUpdate(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            mySolution.Locks.ExecuteOrQueueReadLockEx(Lifetime,
                "UnrealPluginInstaller.InstallPluginIfRequired",
                () => HandleManualInstallPlugin(unrealPluginInstallInfo.Location));
        }

        private void InstallPluginInEngineIfRequired(UnrealPluginInstallInfo unrealPluginInstallInfo,
            IProperty<double> progress, bool forceInstall = false)
        {
            if (!forceInstall && unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable &&
                unrealPluginInstallInfo.EnginePlugin.PluginVersion == myPathsProvider.CurrentPluginVersion) return;
            
            InstallPluginInEngine(unrealPluginInstallInfo, progress);
        }
        
        private void InstallPluginInGameIfRequired(UnrealPluginInstallInfo unrealPluginInstallInfo,
            Property<double> progress, bool forceInstall = false)
        {
            if (!forceInstall && unrealPluginInstallInfo.ProjectPlugins.All(description =>
                description.IsPluginAvailable && description.PluginVersion == myPathsProvider.CurrentPluginVersion))
                return;

            InstallPluginInGame(unrealPluginInstallInfo, progress);
        }

        private void InstallPluginInGame(UnrealPluginInstallInfo unrealPluginInstallInfo, Property<double> progress)
        {
            var backupDir = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
            using var deleteTempFolders = new DeleteTempFolders(backupDir.Directory);

            var backupAllPlugins = BackupAllPlugins(unrealPluginInstallInfo);
            var success = true;
            var size = unrealPluginInstallInfo.ProjectPlugins.Count;
            var range = 1.0 / size;
            for (int i = 0; i < unrealPluginInstallInfo.ProjectPlugins.Count; i++)
            {
                progress.Value = range*i;
                var installDescription = unrealPluginInstallInfo.ProjectPlugins[i];
                if (InstallPlugin(installDescription, installDescription.UprojectFilePath, progress, range)) continue;
                
                success = false;
                break;
            }

            if (success)
            {
                unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable = false;
            } else
            {
                foreach (var backupAllPlugin in backupAllPlugins)
                {
                    backupAllPlugin.Restore();
                }
            }
        }

        private List<BackupDir> BackupAllPlugins(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            var result = new List<BackupDir>();
            if (unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable)
            {
               result.Add(new BackupDir(unrealPluginInstallInfo.EnginePlugin.UnrealPluginRootFolder, TMP_PREFIX)); 
            }
            foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
            {
                if(installDescription.IsPluginAvailable)
                    result.Add(new BackupDir(installDescription.UnrealPluginRootFolder, TMP_PREFIX));
            }

            return result;
        }

        private void InstallPluginInEngine(UnrealPluginInstallInfo unrealPluginInstallInfo, IProperty<double> progress)
        {
            var backupDir = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
            using var deleteTempFolders = new DeleteTempFolders(backupDir.Directory);

            var backupAllPlugins = BackupAllPlugins(unrealPluginInstallInfo);
            progress.Value = 0.0;
            if (!InstallPlugin(unrealPluginInstallInfo.EnginePlugin, unrealPluginInstallInfo.ProjectPlugins.First().UprojectFilePath, progress, 1.0))
            {
                foreach (var backupAllPlugin in backupAllPlugins)
                {
                    backupAllPlugin.Restore();
                }
            }
            else
            {
                foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
                {
                    installDescription.IsPluginAvailable = false;
                }
            }
        }

        private bool InstallPlugin(UnrealPluginInstallInfo.InstallDescription installDescription,
            FileSystemPath uprojectFile, IProperty<double> progressProperty, double range)
        {
            var ZIP_STEP = 0.1*range;
            var PATCH_STEP = 0.1*range;
            var BUILD_STEP = 0.6*range;
            var REFRESH_STEP = 0.1*range;

            var pluginRootFolder = installDescription.UnrealPluginRootFolder;

            var editorPluginPathFile = myPathsProvider.PathToPackedPlugin;
            var pluginTmpDir = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
            try
            {
                ZipFile.ExtractToDirectory(editorPluginPathFile.FullPath, pluginTmpDir.FullPath);
                progressProperty.Value += ZIP_STEP;
            }
            catch (Exception exception)
            {
                myLogger.Error(exception, $"[UnrealLink]: Couldn't extract {editorPluginPathFile} to {pluginTmpDir}");

                const string unzipFailTitle = "Failed to unzip new RiderLink plugin";
                var unzipFailText =
                    $"<html>Failed to unzip <a href=\"{editorPluginPathFile.FullPath}\">new version of RiderLink</a> to user folder<br>" +
                    "Try restarting Rider in administrative mode</html>";
                Notify(unzipFailTitle, unzipFailText, RdNotificationEntryType.WARN);
                return false;
            }

            var upluginFile = UnrealPluginDetector.GetPathToUpluginFile(pluginTmpDir);
            var pluginBuildOutput = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
            var buildProgress = progressProperty.Value;
            if (!BuildPlugin(upluginFile,
                pluginBuildOutput,
                uprojectFile,value => progressProperty.SetValue(buildProgress + value*BUILD_STEP)))
            {
                myLogger.Error($"Failed to build RiderLink for any available project");
                const string failedBuildTitle = "Failed to build RiderLink plugin";
                var failedBuildText = "<html>" +
                                      "Check build logs for more info<br>" +
                                      "<b>Help > Diagnostic Tools > Show Log in Explorer</b>" +
                                      "</html>";
                Notify(failedBuildTitle, failedBuildText, RdNotificationEntryType.ERROR);
                return false;
            }
            progressProperty.Value = buildProgress + BUILD_STEP;

            if (!PatchEnabledByDefaultOfUpluginFile(pluginBuildOutput))
            {
                const string failedToPatch = "Failed to build RiderLink plugin";
                var failedPatchText = "<html>" +
                                      "Failed to set `EnableByDefault` to true in RiderLink.uplugin<br>" +
                                      "You need to manually enable RiderLink in UnrealEditor<br>" +
                                      "</html>";
                Notify(failedToPatch, failedPatchText, RdNotificationEntryType.INFO);
            }
            progressProperty.Value += PATCH_STEP;

            pluginRootFolder.CreateDirectory().DeleteChildren();
            pluginBuildOutput.Copy(pluginRootFolder);
            progressProperty.Value += REFRESH_STEP;

            installDescription.IsPluginAvailable = true;
            installDescription.PluginVersion = myPathsProvider.CurrentPluginVersion;

            const string title = "RiderLink plugin installed";
            var text = "<html>RiderLink plugin was installed to:<br>" +
                       $"<b>{pluginRootFolder}<b>" +
                       "</html>";

            Notify(title, text, RdNotificationEntryType.INFO);

            RegenerateProjectFiles(uprojectFile);
            return true;
        }

        private bool PatchEnabledByDefaultOfUpluginFile(FileSystemPath pluginBuildOutput)
        {
            var upluginFile = pluginBuildOutput / "RiderLink.uplugin";
            if (!upluginFile.ExistsFile) return false;

            var jsonText = File.ReadAllText(upluginFile.FullPath);
            try
            {
                var jsonObject = Newtonsoft.Json.JsonConvert.DeserializeObject(jsonText) as JObject;
                if (jsonObject == null)
                {
                    myLogger.Warn($"[UnrealLink]: {upluginFile} is not a JSON file, couldn't patch it");
                    return false;
                }
                jsonObject["EnabledByDefault"] = true;
                File.WriteAllText(upluginFile.FullPath, jsonObject.ToString());
            }
            catch (Exception e)
            {
                myLogger.Warn($"[UnrealLink]: Couldn't patch 'EnableByDefault' field of {upluginFile}", e);
                return false;
            }

            return true;
        }

        private void Notify(string title, string text, RdNotificationEntryType verbosity)
        {
            var notification = new NotificationModel(title, text, true, verbosity);

            mySolution.Locks.ExecuteOrQueue(Lifetime, "UnrealLink.InstallPlugin",
                () => { myNotificationsModel.Notification(notification); });
        }

        private void BindToInstallationSettingChange()
        {
            var entry = myBoundSettingsStore.Schema.GetScalarEntry((UnrealLinkSettings s) => s.InstallRiderLinkPlugin);
            myBoundSettingsStore.GetValueProperty<bool>(Lifetime, entry, null).Change.Advise_When(Lifetime,
                newValue => newValue, args => { InstallPluginIfInfoAvailable(); });
        }

        private void InstallPluginIfInfoAvailable()
        {
            var unrealPluginInstallInfo = myPluginDetector.InstallInfoProperty.Value;
            if (unrealPluginInstallInfo != null)
            {
                HandleAutoUpdatePlugin(unrealPluginInstallInfo);
            }
        }

        public void HandleManualInstallPlugin(PluginInstallLocation location, bool forceInstall = false)
        {
            var unrealPluginInstallInfo = myPluginDetector.InstallInfoProperty.Value;
            if (unrealPluginInstallInfo == null) return;

            Lifetime.UsingNestedAsync(async lifetime =>
            {
                lifetime.Bracket(() => InstallationIsInProgress.Value = true,
                    () => InstallationIsInProgress.Value = false);
                var prefix = unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable ? "Updating" : "Installing";
                var header = $"{prefix} RiderLink plugin";
                var progress = new Property<double>("UnrealLink.InstallPluginProgress", 0.0);
                var task = RiderBackgroundTaskBuilder.Create()
                    .AsNonCancelable()
                    .WithHeader(header)
                    .WithProgress(progress)
                    .WithDescriptionFromProgress();
                myBackgroundTaskHost.AddNewTask(lifetime, task);
                await lifetime.StartBackground(() =>
                {
                    if (location == PluginInstallLocation.Engine)
                    {
                        InstallPluginInEngineIfRequired(unrealPluginInstallInfo, progress, forceInstall);
                    }
                    else
                    {
                        InstallPluginInGameIfRequired(unrealPluginInstallInfo, progress, forceInstall);
                    }
                });
            });
        }

        private void BindToNotificationFixAction()
        {
            myUnrealHost.PerformModelAction(model =>
            {
                model.InstallEditorPlugin.Advise(Lifetime, location => HandleManualInstallPlugin(location));
                model.EnableAutoupdatePlugin.AdviseNotNull(Lifetime,
                    unit =>
                    {
                        myBoundSettingsStore.SetValue<UnrealLinkSettings, bool>(s => s.InstallRiderLinkPlugin, true);
                    });
            });
        }

        private void RegenerateProjectFiles(FileSystemPath uprojectFilePath)
        {
            if (uprojectFilePath.IsNullOrEmpty())
            {
                myLogger.Error(
                    $"[UnrealLink]: Failed refresh project files, couldn't find uproject path: {uprojectFilePath}");
                return;
            }

            var engineRoot = CppUE4FolderFinder.FindUnrealEngineRoot(uprojectFilePath);
            if (engineRoot.IsEmpty)
            {
                myLogger.Error($"[UnrealLink]: Couldn't find Unreal Engine root for {uprojectFilePath}");
                var notificationNoEngine = new NotificationModel($"Failed to refresh project files",
                    "<html>RiderLink has been successfully installed to the project:<br>" +
                    $"<b>{uprojectFilePath.NameWithoutExtension}<b>" +
                    "but refresh project action has failed.<br>" +
                    "Couldn't find Unreal Engine root for:<br>" +
                    $"{uprojectFilePath}<br>" +
                    "</html>", true, RdNotificationEntryType.WARN);

                mySolution.Locks.ExecuteOrQueue(Lifetime, "UnrealLink.RefreshProject",
                    () => { myNotificationsModel.Notification(notificationNoEngine); });
                return;
            }

            var pathToUnrealBuildToolBin = CppUE4FolderFinder.GetAbsolutePathToUnrealBuildToolBin(engineRoot);

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

            mySolution.Locks.ExecuteOrQueue(Lifetime, "UnrealLink.RefreshProject",
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
                var commandLine = new CommandLineBuilderJet()
                    .AppendFileName(generateProjectFilesBat);
            
                var hackCmd = new CommandLineBuilderJet()
                    .AppendSwitch("/C")
                    .AppendSwitch($"\"{commandLine}\"");
                
                myLogger.Info($"[UnrealLink]: Regenerating project files: {commandLine}");

                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(BatchUtils.GetPathToCmd(),
                    hackCmd,
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
                .AppendFileName(pathToUnrealVersionSelector)
                .AppendSwitch("/projectFiles")
                .AppendFileName(uprojectFilePath);
            
            var hackCmd = new CommandLineBuilderJet()
                .AppendSwitch("/C")
                .AppendSwitch($"\"{commandLine}\"");

            try
            {
                myLogger.Info($"[UnrealLink]: Regenerating project files: {commandLine}");
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(BatchUtils.GetPathToCmd(),
                    hackCmd,
                    LoggingLevel.INFO,
                    TimeSpan.FromMinutes(1),
                    InvokeChildProcess.TreatStderr.AsOutput,
                    pathToUnrealVersionSelector.Directory
                ));
            }
            catch (ErrorLevelException errorLevelException)
            {
                myLogger.Error(errorLevelException,
                    $"[UnrealLink]: Failed refresh project files: calling {pathToUnrealVersionSelector} {commandLine}");
                return false;
            }

            return true;
        }

        private bool RegenerateProjectUsingUBT(FileSystemPath uprojectFilePath, FileSystemPath pathToUnrealBuildToolBin,
            FileSystemPath engineRoot)
        {
            bool isInstalledBuild = IsInstalledBuild(engineRoot);

            var commandLine = new CommandLineBuilderJet()
                .AppendFileName(pathToUnrealBuildToolBin)
                .AppendSwitch("-ProjectFiles")
                .AppendSwitch($"-project=\"{uprojectFilePath.FullPath}\"")
                .AppendSwitch("-game");

            if (isInstalledBuild)
                commandLine.AppendSwitch("-rocket");
            else
                commandLine.AppendSwitch("-engine");

            var hackCmd = new CommandLineBuilderJet()
                .AppendSwitch("/C")
                .AppendSwitch($"\"{commandLine}\"");

            try
            {
                myLogger.Info($"[UnrealLink]: Regenerating project files: {commandLine}");
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(BatchUtils.GetPathToCmd(),
                    hackCmd,
                    LoggingLevel.INFO,
                    TimeSpan.FromMinutes(1),
                    InvokeChildProcess.TreatStderr.AsOutput,
                    pathToUnrealBuildToolBin.Directory
                ));
            }
            catch (ErrorLevelException errorLevelException)
            {
                myLogger.Error(errorLevelException,
                    $"[UnrealLink]: Failed refresh project files: calling {commandLine}");
                return false;
            }

            return true;
        }

        private static bool IsInstalledBuild(FileSystemPath engineRoot)
        {
            var installedBuildTxt = engineRoot / "Engine" / "Build" / "InstalledBuild.txt";
            var isInstalledBuild = installedBuildTxt.ExistsFile;
            return isInstalledBuild;
        }

        private bool BuildPlugin(FileSystemPath upluginPath, FileSystemPath outputDir, FileSystemPath uprojectFile, Action<double> progressPump)
        {
            //engineRoot\Engine\Build\BatchFiles\RunUAT.bat" BuildPlugin -Plugin="D:\tmp\RiderLink\RiderLink.uplugin" -Package="D:\PROJECTS\UE\FPS_D_TEST\Plugins\Developer\RiderLink" -Rocket
            var engineRoot = CppUE4FolderFinder.FindUnrealEngineRoot(uprojectFile);
            if (engineRoot.IsEmpty)
            {
                myLogger.Error(
                    $"[UnrealLink]: Failed to build plugin for {uprojectFile}, couldn't find Unreal Engine root");
                return false;
                
            }

            var pathToUat = engineRoot / "Engine" / "Build" / "BatchFiles" / "RunUAT.bat";
            if (!pathToUat.ExistsFile)
            {
                myLogger.Error("[UnrealLink]: Failed build plugin: RunUAT.bat is not available");
                return false;
            }
            
            var commandLine = new CommandLineBuilderJet()
                .AppendFileName(pathToUat)
                .AppendSwitch("BuildPlugin")
                .AppendSwitch($"-Plugin=\"{upluginPath.FullPath}\"")
                .AppendSwitch($"-Package=\"{outputDir.FullPath}\"")
                .AppendSwitch("-Rocket");
            
            var hackCmd = new CommandLineBuilderJet()
                .AppendSwitch("/C")
                .AppendSwitch($"\"{commandLine}\"");

            List<string> stdOut = new List<string>();
            List<string> stdErr = new List<string>();
            try
            {
                var pipeStreams = InvokeChildProcess.PipeStreams.Custom((chunk, isErr, logger) =>
                {
                    if (isErr)
                    {
                        stdErr.Add(chunk);
                    }
                    else
                    {
                        stdOut.Add(chunk);
                    }

                    if (isErr) return;
                    
                    var progressText = chunk.Trim();
                    if (!progressText.StartsWith("[")) return;
                    
                    var closingBracketIndex = progressText.IndexOf(']');
                    if (closingBracketIndex == -1) return;
                    
                    var progressNumberWithDivision = progressText.Substring(1, closingBracketIndex-1);
                    var numbers = progressNumberWithDivision.Split('/');
                    if (numbers.Length != 2) return;

                    if (!int.TryParse(numbers[0], out var leftInt)) return;
                    if (!int.TryParse(numbers[1], out var rightInt)) return;

                    progressPump((double)leftInt / rightInt);
                });
                myLogger.Info($"[UnrealLink]: Building UnrealLink plugin with: {commandLine}");
                var pathToCmdExe = BatchUtils.GetPathToCmd();

                myLogger.Verbose("[UnrealLink]: Start building UnrealLink");
                var result = InvokeChildProcess.InvokeSync(pathToCmdExe, hackCmd,
                    pipeStreams,TimeSpan.FromMinutes(30), null, null, null, myLogger);
                myLogger.Verbose("[UnrealLink]: Stop building UnrealLink");
                myLogger.Verbose("[UnrealLink]: Build logs:");
                myLogger.Verbose(stdOut.Join("\n"));
                if(!stdErr.IsEmpty())
                    myLogger.Error(stdErr.Join("\n"));
                if (result != 0)
                {
                    myLogger.Error($"[UnrealLink]: Failed to build plugin for {uprojectFile}");
                    return false;
                }
            }
            catch (Exception exception)
            {
                myLogger.Verbose("[UnrealLink]: Stop building UnrealLink");
                myLogger.Verbose("[UnrealLink]: Build logs:");
                myLogger.Verbose(stdOut.Join("\n"));
                if(!stdErr.IsEmpty())
                    myLogger.Error(stdErr.Join("\n"));
                myLogger.Error(exception,
                    $"[UnrealLink]: Failed to build plugin for {uprojectFile}");
                return false;
            }

            return true;
        }
    }
}