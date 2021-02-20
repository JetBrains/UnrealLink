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
using JetBrains.ReSharper.Feature.Services.Cpp.Util;
using JetBrains.ReSharper.Host.Features.BackgroundTasks;
using JetBrains.ReSharper.Psi.Cpp.UE4;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Model.Notifications;
using JetBrains.Util;
using JetBrains.Util.Interop;
using Newtonsoft.Json.Linq;
using RiderPlugin.UnrealLink.Model.FrontendBackend;
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
                    () => { HandleAutoUpdatePlugin(installInfo.New); });
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
                    if (isNotSynced)
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
                () => HandleManualInstallPlugin(
                    new InstallPluginDescription(unrealPluginInstallInfo.Location, ForceInstall.No)
                ));
        }

        private void InstallPluginInEngineIfRequired(UnrealPluginInstallInfo unrealPluginInstallInfo,
            IProperty<double> progress, ForceInstall forceInstall)
        {
            if (forceInstall == ForceInstall.No && unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable &&
                unrealPluginInstallInfo.EnginePlugin.PluginVersion == myPathsProvider.CurrentPluginVersion)
            {
                myLogger.Info("[UnrealLink] Plugin is up to date");
                myLogger.Info(
                    $"[UnrealLInk] Installed in Engine plugin version: {unrealPluginInstallInfo.EnginePlugin.PluginVersion}");
                return;
            }

            InstallPluginInEngine(unrealPluginInstallInfo, progress);
        }

        private void InstallPluginInGameIfRequired(UnrealPluginInstallInfo unrealPluginInstallInfo,
            Property<double> progress, ForceInstall forceInstall)
        {
            if (forceInstall == ForceInstall.No && unrealPluginInstallInfo.ProjectPlugins.All(description =>
                description.IsPluginAvailable && description.PluginVersion == myPathsProvider.CurrentPluginVersion))
            {
                myLogger.Info("[UnrealLink] Plugin is up to date");
                foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
                {
                    myLogger.Info(
                        $"[UnrealLInk] Installed in {installDescription.UprojectFilePath.NameWithoutExtension} plugin version: {unrealPluginInstallInfo.EnginePlugin.PluginVersion}");
                }

                return;
            }

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
                progress.Value = range * i;
                var installDescription = unrealPluginInstallInfo.ProjectPlugins[i];
                if (InstallPlugin(installDescription, installDescription.UprojectFilePath, progress, range)) continue;

                success = false;
                break;
            }

            if (success)
            {
                unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable = false;
            }
            else
            {
                foreach (var backupAllPlugin in backupAllPlugins)
                {
                    backupAllPlugin.Restore();
                }
            }
            myUnrealHost.myModel.InstallPluginFinished(success);
        }

        private List<BackupDir> BackupAllPlugins(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            var result = new List<BackupDir>();
            if (unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable)
            {
                try
                {
                    result.Add(new BackupDir(unrealPluginInstallInfo.EnginePlugin.UnrealPluginRootFolder, TMP_PREFIX));
                }
                catch
                {
                    var text = "Close all running instances of Unreal Editor and try again\n" +
                               $"Path to old plugin: {unrealPluginInstallInfo.EnginePlugin.UnrealPluginRootFolder}";

                    myUnrealHost.myModel.RiderLinkInstallMessage(
                        new InstallMessage("Failed to backup old RiderLink plugin", ContentType.Error));
                    myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(text, ContentType.Error));
                    throw;
                }
            }

            foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
            {
                try
                {
                    if (installDescription.IsPluginAvailable)
                        result.Add(new BackupDir(installDescription.UnrealPluginRootFolder, TMP_PREFIX));
                }
                catch
                {
                    var text = "Close all running instances of Unreal Editor and try again\n" +
                               $"Path to old plugin: {installDescription.UnrealPluginRootFolder}";

                    myUnrealHost.myModel.RiderLinkInstallMessage(
                        new InstallMessage("Failed to backup old RiderLink plugin", ContentType.Error));
                    myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(text, ContentType.Error));
                    throw;
                }
            }

            return result;
        }

        private void InstallPluginInEngine(UnrealPluginInstallInfo unrealPluginInstallInfo, IProperty<double> progress)
        {
            var backupDir = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
            using var deleteTempFolders = new DeleteTempFolders(backupDir.Directory);

            var backupAllPlugins = BackupAllPlugins(unrealPluginInstallInfo);
            progress.Value = 0.0;
            var success = InstallPlugin(unrealPluginInstallInfo.EnginePlugin,
            unrealPluginInstallInfo.ProjectPlugins.First().UprojectFilePath, progress, 1.0);
            if (!success)
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
            myUnrealHost.myModel.InstallPluginFinished(success);
        }

        private bool InstallPlugin(UnrealPluginInstallInfo.InstallDescription installDescription,
            FileSystemPath uprojectFile, IProperty<double> progressProperty, double range)
        {
            var ZIP_STEP = 0.1 * range;
            var PATCH_STEP = 0.1 * range;
            var BUILD_STEP = 0.6 * range;
            var REFRESH_STEP = 0.1 * range;

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
                    $"Failed to unzip new version of RiderLink ({editorPluginPathFile.FullPath}) to user folder ({pluginTmpDir.FullPath})\n" +
                    "Try restarting Rider in administrative mode";

                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(unzipFailTitle, ContentType.Error));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(unzipFailText, ContentType.Error));
                return false;
            }

            var upluginFile = UnrealPluginDetector.GetPathToUpluginFile(pluginTmpDir);
            var pluginBuildOutput = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
            var buildProgress = progressProperty.Value;
            if (!BuildPlugin(upluginFile,
                pluginBuildOutput,
                uprojectFile, value => progressProperty.SetValue(buildProgress + value * BUILD_STEP)))
            {
                myLogger.Error($"Failed to build RiderLink for any available project");
                const string failedBuildText = "Failed to build RiderLink plugin";
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(failedBuildText, ContentType.Error));
                return false;
            }

            progressProperty.Value = buildProgress + BUILD_STEP;

            if (!PatchUpluginFileAfterInstallation(pluginBuildOutput))
            {
                const string failedToPatch = "Failed to patch RiderLink.uplugin";
                var failedPatchText = "Failed to set `EnableByDefault` to true in RiderLink.uplugin\n" +
                                      "You need to manually enable RiderLink in UnrealEditor";
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(failedToPatch, ContentType.Normal));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(failedPatchText, ContentType.Normal));
            }

            progressProperty.Value += PATCH_STEP;

            pluginRootFolder.CreateDirectory().DeleteChildren();
            pluginBuildOutput.Copy(pluginRootFolder);
            progressProperty.Value += REFRESH_STEP;

            installDescription.IsPluginAvailable = true;
            installDescription.PluginVersion = myPathsProvider.CurrentPluginVersion;

            const string title = "RiderLink plugin installed";
            var text = $"RiderLink plugin was installed to: {pluginRootFolder}";

            myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(title, ContentType.Normal));
            myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(text, ContentType.Normal));

            var notification = new NotificationModel(title, text, true, RdNotificationEntryType.INFO,
                new List<NotificationHyperlink>());

            mySolution.Locks.ExecuteOrQueue(Lifetime, "UnrealLink.InstallPlugin",
                () => { myNotificationsModel.Notification(notification); });

            mySolution.Locks.ExecuteOrQueueReadLock(Lifetime, "UnrealLink.RegenerateProjectFiles", () =>
            {
                var cppUe4SolutionDetector = mySolution.GetComponent<CppUE4SolutionDetector>();
                if (cppUe4SolutionDetector.SupportRiderProjectModel != CppUE4ProjectModelSupportMode.UprojectOpened)
                    RegenerateProjectFiles(uprojectFile);
            });
            return true;
        }

        private bool PatchUpluginFileAfterInstallation(FileSystemPath pluginBuildOutput)
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
                jsonObject["Installed"] = false;
                File.WriteAllText(upluginFile.FullPath, jsonObject.ToString());
            }
            catch (Exception e)
            {
                myLogger.Warn($"[UnrealLink]: Couldn't patch 'EnableByDefault' field of {upluginFile}", e);
                return false;
            }

            return true;
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

        public void HandleManualInstallPlugin(InstallPluginDescription installPluginDescription)
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
                myUnrealHost.myModel.RiderLinkInstallPanelInit();
                await lifetime.StartBackground(() =>
                {
                    if (installPluginDescription.Location == PluginInstallLocation.Engine)
                    {
                        InstallPluginInEngineIfRequired(unrealPluginInstallInfo, progress,
                            installPluginDescription.ForceInstall);
                    }
                    else
                    {
                        InstallPluginInGameIfRequired(unrealPluginInstallInfo, progress,
                            installPluginDescription.ForceInstall);
                    }
                });
            });
        }

        private void BindToNotificationFixAction()
        {
            myUnrealHost.PerformModelAction(model =>
            {
                model.InstallEditorPlugin.Advise(Lifetime,
                    installPluginDescription => HandleManualInstallPlugin(installPluginDescription));
                model.EnableAutoupdatePlugin.AdviseNotNull(Lifetime,
                    unit =>
                    {
                        myBoundSettingsStore.SetValue<UnrealLinkSettings, bool>(s => s.InstallRiderLinkPlugin, true);
                    });
            });
        }

        private void RegenerateProjectFiles(FileSystemPath uprojectFilePath)
        {
            void LogFailedRefreshProjectFiles()
            {
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage("Failed to refresh project files",
                    ContentType.Normal));
                myUnrealHost.myModel.RiderLinkInstallMessage(
                    new InstallMessage("RiderLink will not be visible in solution explorer", ContentType.Normal));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(
                    "Need to refresh project files in Unreal Editor or in File Explorer with context action for .uproject file 'Refresh Project files'",
                    ContentType.Normal));
            }

            if (uprojectFilePath.IsNullOrEmpty())
            {
                myLogger.Warn(
                    $"[UnrealLink]: Failed refresh project files, couldn't find uproject path: {uprojectFilePath}");

                LogFailedRefreshProjectFiles();
                return;
            }

            var engineRoot = CppUE4FolderFinder.FindUnrealEngineRoot(uprojectFilePath);
            if (engineRoot.IsEmpty)
            {
                myLogger.Warn($"[UnrealLink]: Couldn't find Unreal Engine root for {uprojectFilePath}");

                LogFailedRefreshProjectFiles();
                return;
            }

            var pathToUnrealBuildToolBin = CppUE4FolderFinder.GetAbsolutePathToUnrealBuildToolBin(engineRoot);

            // 1. If project is under engine root, use GenerateProjectFiles.{extension} first
            if (GenerateProjectFilesCmd(engineRoot)) return;
            // 2. If it's a standalone project, use UnrealVersionSelector
            //    The same way "Generate project files" from context menu of .uproject works
            if (RegenerateProjectUsingUVS(uprojectFilePath, engineRoot)) return;
            // 3. If UVS is missing or have failed, fallback to UnrealBuildTool
            if (RegenerateProjectUsingUBT(uprojectFilePath, pathToUnrealBuildToolBin, engineRoot)) return;

            myLogger.Warn("[UnrealLink]: Couldn't refresh project files");

            LogFailedRefreshProjectFiles();
        }

        private bool GenerateProjectFilesCmd(FileSystemPath engineRoot)
        {
            var isProjectUnderEngine = mySolution.SolutionFilePath.Directory == engineRoot;
            if (!isProjectUnderEngine)
            {
                myLogger.Info($"[UnrealLink]: {mySolution.SolutionFilePath} is not in {engineRoot} ");
                return false;
            }

            var generateProjectFilesCmd = engineRoot / $"GenerateProjectFiles.{GetPlatformCmdExtension()}";
            if (!generateProjectFilesCmd.ExistsFile)
            {
                myLogger.Info($"[UnrealLink]: {generateProjectFilesCmd} is not available");
                return false;
            }

            try
            {
                var command = GetPlatformCommand(generateProjectFilesCmd);
                var commandLine = GetPlatformCommandLine(generateProjectFilesCmd);

                myLogger.Info($"[UnrealLink]: Regenerating project files: {commandLine}");

                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(command,
                    commandLine,
                    LoggingLevel.INFO,
                    TimeSpan.FromMinutes(1),
                    InvokeChildProcess.TreatStderr.AsOutput,
                    generateProjectFilesCmd.Directory
                ));
            }
            catch (ErrorLevelException errorLevelException)
            {
                myLogger.Error(errorLevelException,
                    $"[UnrealLink]: Failed refresh project files, calling {generateProjectFilesCmd} went wrong");
                return false;
            }

            return true;
        }

        private bool RegenerateProjectUsingUVS(FileSystemPath uprojectFilePath, FileSystemPath engineRoot)
        {
            if (PlatformUtil.RuntimePlatform != PlatformUtil.Platform.Windows) return false;

            var pathToUnrealVersionSelector =
                engineRoot / "Engine" / "Binaries" / "Win64" / "UnrealVersionSelector.exe";
            if (!pathToUnrealVersionSelector.ExistsFile)
            {
                myLogger.Info($"[UnrealLink]: {pathToUnrealVersionSelector} is not available");
                return false;
            }

            var commandLine =
                GetPlatformCommandLine(pathToUnrealVersionSelector, "/projectFiles", $"\"{uprojectFilePath}\"");

            try
            {
                myLogger.Info($"[UnrealLink]: Regenerating project files: {commandLine}");
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    BatchUtils.GetPathToCmd(),
                    commandLine,
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
                .AppendSwitch("-ProjectFiles")
                .AppendSwitch($"-project=\"{uprojectFilePath.FullPath}\"")
                .AppendSwitch("-game");

            commandLine.AppendSwitch(isInstalledBuild ? "-rocket" : "-engine");

            try
            {
                myLogger.Info($"[UnrealLink]: Regenerating project files: {commandLine}");
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    pathToUnrealBuildToolBin,
                    commandLine,
                    LoggingLevel.INFO,
                    TimeSpan.FromMinutes(1),
                    InvokeChildProcess.TreatStderr.AsOutput,
                    pathToUnrealBuildToolBin.Directory
                ));
            }
            catch (Exception errorLevelException)
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

        private bool BuildPlugin(FileSystemPath upluginPath, FileSystemPath outputDir, FileSystemPath uprojectFile,
            Action<double> progressPump)
        {
            //engineRoot\Engine\Build\BatchFiles\RunUAT.{extension}" BuildPlugin -Plugin="D:\tmp\RiderLink\RiderLink.uplugin" -Package="D:\PROJECTS\UE\FPS_D_TEST\Plugins\Developer\RiderLink" -Rocket
            var engineRoot = CppUE4FolderFinder.FindUnrealEngineRoot(uprojectFile);
            if (engineRoot.IsEmpty)
            {
                myLogger.Error(
                    $"[UnrealLink]: Failed to build plugin for {uprojectFile}, couldn't find Unreal Engine root");
                var text = $"Couldn't find Unreal Engine root for {uprojectFile}";
                myUnrealHost.myModel.RiderLinkInstallMessage(
                    new InstallMessage($"Failed to build RiderLink plugin for {uprojectFile}", ContentType.Error));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(text, ContentType.Error));
                return false;
            }

            var runUatName = $"RunUAT.{GetPlatformCmdExtension()}";
            var pathToUat = engineRoot / "Engine" / "Build" / "BatchFiles" / runUatName;
            if (!pathToUat.ExistsFile)
            {
                myLogger.Error($"[UnrealLink]: Failed build plugin: {runUatName} is not available");
                var text = $"{runUatName} is not available is not available at expected destination: {pathToUat}<br>";
                myUnrealHost.myModel.RiderLinkInstallMessage(
                    new InstallMessage($"Failed to build RiderLink plugin for {uprojectFile}", ContentType.Error));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(text, ContentType.Error));
                return false;
            }

            var command = GetPlatformCommand(pathToUat);
            var commandLine = GetPlatformCommandLine(pathToUat, "BuildPlugin", $"-Plugin=\"{upluginPath.FullPath}\"",
                $"-Package=\"{outputDir.FullPath}\"", "-Rocket");

            List<string> stdOut = new List<string>();
            List<string> stdErr = new List<string>();
            try
            {
                var pipeStreams = InvokeChildProcess.PipeStreams.Custom((chunk, isErr, logger) =>
                {
                    myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(chunk,
                        isErr ? ContentType.Error : ContentType.Normal));
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

                    var progressNumberWithDivision = progressText.Substring(1, closingBracketIndex - 1);
                    var numbers = progressNumberWithDivision.Split('/');
                    if (numbers.Length != 2) return;

                    if (!int.TryParse(numbers[0], out var leftInt)) return;
                    if (!int.TryParse(numbers[1], out var rightInt)) return;

                    progressPump((double) leftInt / rightInt);
                });
                myLogger.Info($"[UnrealLink]: Building UnrealLink plugin with: {commandLine}");

                myLogger.Verbose("[UnrealLink]: Start building UnrealLink");
                var result = InvokeChildProcess.InvokeSync(command, commandLine,
                    pipeStreams, TimeSpan.FromMinutes(30), null, null, null, myLogger);
                myLogger.Verbose("[UnrealLink]: Stop building UnrealLink");
                myLogger.Verbose("[UnrealLink]: Build logs:");
                myLogger.Verbose(stdOut.Join("\n"));
                if (!stdErr.IsEmpty())
                    myLogger.Error(stdErr.Join("\n"));
                if (result != 0)
                {
                    myLogger.Error($"[UnrealLink]: Failed to build plugin for {uprojectFile}");
                    myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage("Failed to build RiderLink plugin",
                        ContentType.Error));
                    return false;
                }
            }
            catch (Exception exception)
            {
                myLogger.Verbose("[UnrealLink]: Stop building UnrealLink");
                myLogger.Verbose("[UnrealLink]: Build logs:");
                myLogger.Verbose(stdOut.Join("\n"));
                if (!stdErr.IsEmpty())
                    myLogger.Error(stdErr.Join("\n"));
                myLogger.Error(exception,
                    $"[UnrealLink]: Failed to build plugin for {uprojectFile}");

                myUnrealHost.myModel.RiderLinkInstallMessage(
                    new InstallMessage($"Failed to build RiderLink plugin for {uprojectFile}", ContentType.Error));
                return false;
            }

            return true;
        }

        private CommandLineBuilderJet GetPlatformCommandLine(FileSystemPath command, params string[] args)
        {
            var commandLine = new CommandLineBuilderJet();
            if (PlatformUtil.RuntimePlatform == PlatformUtil.Platform.Windows)
            {
                commandLine.AppendFileName(command);
            }

            foreach (var arg in args)
            {
                commandLine.AppendSwitch(arg);
            }

            if (PlatformUtil.RuntimePlatform == PlatformUtil.Platform.Windows)
            {
                return new CommandLineBuilderJet().AppendSwitch("/C")
                    .AppendSwitch($"\"{commandLine}\"");
            }

            return commandLine;
        }

        private FileSystemPath GetPlatformCommand(FileSystemPath command)
        {
            return PlatformUtil.RuntimePlatform == PlatformUtil.Platform.Windows ? BatchUtils.GetPathToCmd() : command;
        }

        private string GetPlatformCmdExtension()
        {
            switch (PlatformUtil.RuntimePlatform)
            {
                case PlatformUtil.Platform.Windows:
                    return "bat";
                case PlatformUtil.Platform.MacOsX:
                    return "command";
                default:
                    return "sh";
            }
        }
    }
}