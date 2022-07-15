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
using JetBrains.Rd.Base;
using JetBrains.RdBackend.Common.Features;
using JetBrains.RdBackend.Common.Features.BackgroundTasks;
using JetBrains.ReSharper.Feature.Services.Cpp.Util;
using JetBrains.ReSharper.Psi.Cpp.UE4;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Backend.Features.BackgroundTasks;
using JetBrains.Rider.Model;
using JetBrains.Rider.Model.Notifications;
using JetBrains.Util;
using Newtonsoft.Json.Linq;
using RiderPlugin.UnrealLink.Model.FrontendBackend;
using RiderPlugin.UnrealLink.Settings;
using RiderPlugin.UnrealLink.Utils;

namespace RiderPlugin.UnrealLink.PluginInstaller
{
    [SolutionComponent]
    public class UnrealPluginInstaller
    {
        public Lifetime Lifetime { get; }
        private readonly ILogger myLogger;
        private readonly PluginPathsProvider myPathsProvider;
        private readonly ISolution mySolution;
        private readonly UnrealHost myUnrealHost;
        private readonly NotificationsModel myNotificationsModel;
        private readonly RiderBackgroundTaskHost myBackgroundTaskHost;
        private IContextBoundSettingsStoreLive myBoundSettingsStore;
        private UnrealPluginDetector myPluginDetector;
        private const string TMP_PREFIX = "UnrealLink";

        public UnrealPluginInstaller(Lifetime lifetime, ILogger logger, UnrealPluginDetector pluginDetector,
            PluginPathsProvider pathsProvider, ISolution solution, ISettingsStore settingsStore, UnrealHost unrealHost,
            NotificationsModel notificationsModel, RiderBackgroundTaskHost backgroundTaskHost)
        {
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
                myUnrealHost.myModel.IsInstallInfoAvailable.Set(true);
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
            if (unrealPluginInstallInfo.Location == PluginInstallLocation.Engine)
            {
                status = PluginInstallStatus.InEngine;
                outOfSync = !unrealPluginInstallInfo.EnginePlugin.PluginChecksum.SequenceEqual(myPathsProvider.CurrentPluginChecksum);
            }

            if (unrealPluginInstallInfo.Location == PluginInstallLocation.Game)
            {
                status = PluginInstallStatus.InGame;
                outOfSync = unrealPluginInstallInfo.ProjectPlugins.Any(description =>
                    !description.PluginChecksum.SequenceEqual(myPathsProvider.CurrentPluginChecksum)
                    );
            }

            if (!outOfSync) return;

            if(myBoundSettingsStore.GetValue((UnrealLinkSettings s) => s.InstallRiderLinkPlugin))
            {
                QueueAutoUpdate(unrealPluginInstallInfo);
                return;
            }

            myLogger.Warn("[UnrealLink]: Plugin is out of sync");
            myUnrealHost.PerformModelAction(model =>
            {
                var isGameAvailable = !unrealPluginInstallInfo.ProjectPlugins.IsEmpty();
                var isEngineAvailable = myPluginDetector.IsValidEngine();
                model.OnEditorPluginOutOfSync(new EditorPluginOutOfSync(status, isGameAvailable, isEngineAvailable));
            });
        }

        private void QueueAutoUpdate(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            mySolution.Locks.ExecuteOrQueueReadLockEx(Lifetime,
                "UnrealPluginInstaller.InstallPluginIfRequired",
                () => HandleManualInstallPlugin(
                    new InstallPluginDescription(unrealPluginInstallInfo.Location, ForceInstall.No)
                ));
        }

        private void InstallPluginInGame(Lifetime lifetime, UnrealPluginInstallInfo unrealPluginInstallInfo,
            Property<double> progress, bool buildRequired)
        {
            myLogger.Verbose("[UnrealLink]: Installing plugin in Game");
            var backupDir = VirtualFileSystemDefinition.CreateTemporaryDirectory(InteractionContext.SolutionContext, null, TMP_PREFIX);
            using var deleteTempFolders = new DeleteTempFolders(backupDir.Directory);

            var backupAllPlugins = BackupAllPlugins(unrealPluginInstallInfo);
            var success = true;
            var size = unrealPluginInstallInfo.ProjectPlugins.Count;
            var range = 1.0 / size;
            for (int i = 0; i < unrealPluginInstallInfo.ProjectPlugins.Count; i++)
            {
                progress.Value = range * i;
                var installDescription = unrealPluginInstallInfo.ProjectPlugins[i];
                myLogger.Verbose($"[UnrealLink]: Installing plugin for {installDescription.ProjectName}");
                try
                {
                    if (buildRequired && InstallPlugin(lifetime, installDescription, unrealPluginInstallInfo.EngineRoot, progress,
                        range)) continue;
                    if (!buildRequired && ExtractPlugin(lifetime, installDescription, unrealPluginInstallInfo.EngineRoot, progress,
                           range)) continue;
                }
                catch (OperationCanceledException)
                {
                    // Operation was cancelled, don't need to do anything, fallback to break case
                }

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

        private void InstallPluginInEngine(Lifetime lifetime, UnrealPluginInstallInfo unrealPluginInstallInfo,
            IProperty<double> progress, bool buildRequired)
        {
            var backupDir = VirtualFileSystemDefinition.CreateTemporaryDirectory(InteractionContext.SolutionContext, null, TMP_PREFIX);
            using var deleteTempFolders = new DeleteTempFolders(backupDir.Directory);

            var backupAllPlugins = BackupAllPlugins(unrealPluginInstallInfo);
            progress.Value = 0.0;
            bool success;
            try
            {
                if (buildRequired)
                {
                    success = InstallPlugin(lifetime, unrealPluginInstallInfo.EnginePlugin,
                        unrealPluginInstallInfo.EngineRoot, progress, 1.0);   
                }
                else
                {
                    success = ExtractPlugin(lifetime, unrealPluginInstallInfo.EnginePlugin,
                        unrealPluginInstallInfo.EngineRoot, progress, 1.0);
                }
            }
            catch (OperationCanceledException)
            {
                success = false;
            }

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

        private bool ExtractPlugin(Lifetime lifetime,
            UnrealPluginInstallInfo.InstallDescription installDescription,
            VirtualFileSystemPath engineRoot, IProperty<double> progressProperty, double range)
        {
            using var def = new LifetimeDefinition();
            var ZIP_STEP = 0.5 * range;
            var PATCH_STEP = 0.5 * range;

            var pluginRootFolder = installDescription.UnrealPluginRootFolder;
            pluginRootFolder.CreateDirectory().DeleteChildren();

            var editorPluginPathFile = myPathsProvider.PathToPackedPlugin;
            try
            {
                ZipFile.ExtractToDirectory(editorPluginPathFile.FullPath, pluginRootFolder.FullPath);
                progressProperty.Value += ZIP_STEP;
            }
            catch (Exception exception)
            {
                myLogger.Warn(exception, $"[UnrealLink]: Couldn't extract {editorPluginPathFile} to {pluginRootFolder}");

                const string unzipFailTitle = "Failed to unzip new RiderLink plugin";
                var unzipFailText =
                    $"Failed to unzip new version of RiderLink ({editorPluginPathFile.FullPath}) to user folder ({pluginRootFolder.FullPath})\n" +
                    "Try restarting Rider in administrative mode";

                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(unzipFailTitle, ContentType.Error));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(unzipFailText, ContentType.Error));
                return false;
            }

            if (!PatchUpluginFileAfterInstallation(pluginRootFolder))
            {
                const string failedToPatch = "Failed to patch RiderLink.uplugin";
                var failedPatchText = "Failed to set `EnableByDefault` to true in RiderLink.uplugin\n" +
                                      "You need to manually enable RiderLink in UnrealEditor";
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(failedToPatch, ContentType.Normal));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(failedPatchText, ContentType.Normal));
            }

            progressProperty.Value += PATCH_STEP;

            lifetime.ToCancellationToken().ThrowIfCancellationRequested();

            installDescription.IsPluginAvailable = true;
            installDescription.PluginChecksum = myPathsProvider.CurrentPluginChecksum;

            const string title = "RiderLink plugin extracted";
            var text = $"RiderLink plugin was extracted to: {pluginRootFolder}";

            myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(title, ContentType.Normal));
            myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(text, ContentType.Normal));

            var notification = new NotificationModel(title, text, true, RdNotificationEntryType.INFO,
                new List<NotificationHyperlink>());

            mySolution.Locks.ExecuteOrQueue(Lifetime, "UnrealLink.InstallPlugin",
                () => { myNotificationsModel.Notification(notification); });

            var cppUe4SolutionDetector = mySolution.GetComponent<CppUE4SolutionDetector>();
            bool isSln;
            using (mySolution.Locks.UsingReadLock())
            {
                isSln = cppUe4SolutionDetector.SupportRiderProjectModel != CppUE4ProjectModelSupportMode.UprojectOpened;
            }

            if (isSln)
            {
                const string refreshText = "Refreshing project files";
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(refreshText, ContentType.Normal));
                mySolution.Locks.ExecuteOrQueue(Lifetime, "Refresh projects after RiderLink installation", () => 
                    UnrealProjectsRefresher.RefreshProjects(Lifetime, myPluginDetector.UnrealVersion, mySolution, installDescription, engineRoot));
            } else {
                var actionTitle = "Update VirtualFileSystem after RiderLink installation";
                mySolution.Locks.Queue(Lifetime, actionTitle, () =>
                {
                    myLogger.Verbose(actionTitle);
                    var fileSystemModel = mySolution.GetProtocolSolution().GetFileSystemModel();
                    fileSystemModel.RefreshPaths.Start(lifetime,
                        new RdFsRefreshRequest(new List<string>() { pluginRootFolder.FullPath }, true));
                });
            }
            return true;
        }

        private bool InstallPlugin(Lifetime lifetime,
            UnrealPluginInstallInfo.InstallDescription installDescription,
            VirtualFileSystemPath engineRoot, IProperty<double> progressProperty, double range)
        {
            using var def = new LifetimeDefinition();
            var ZIP_STEP = 0.1 * range;
            var PATCH_STEP = 0.1 * range;
            var BUILD_STEP = 0.7 * range;

            var pluginRootFolder = installDescription.UnrealPluginRootFolder;

            var editorPluginPathFile = myPathsProvider.PathToPackedPlugin;
            var pluginTmpDir = VirtualFileSystemDefinition.CreateTemporaryDirectory(InteractionContext.SolutionContext, null, TMP_PREFIX);
            def.Lifetime.OnTermination(() => { pluginTmpDir.Delete(); });
            try
            {
                ZipFile.ExtractToDirectory(editorPluginPathFile.FullPath, pluginTmpDir.FullPath);
                progressProperty.Value += ZIP_STEP;
            }
            catch (Exception exception)
            {
                myLogger.Warn(exception, $"[UnrealLink]: Couldn't extract {editorPluginPathFile} to {pluginTmpDir}");

                const string unzipFailTitle = "Failed to unzip new RiderLink plugin";
                var unzipFailText =
                    $"Failed to unzip new version of RiderLink ({editorPluginPathFile.FullPath}) to user folder ({pluginTmpDir.FullPath})\n" +
                    "Try restarting Rider in administrative mode";

                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(unzipFailTitle, ContentType.Error));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(unzipFailText, ContentType.Error));
                return false;
            }

            lifetime.ToCancellationToken().ThrowIfCancellationRequested();

            var upluginFile = UnrealPluginDetector.GetPathToUpluginFile(pluginTmpDir);
            var pluginBuildOutput = VirtualFileSystemDefinition.CreateTemporaryDirectory(InteractionContext.SolutionContext, null, TMP_PREFIX);
            def.Lifetime.OnTermination(() => { pluginBuildOutput.Delete(); });
            var buildProgress = progressProperty.Value;
            var isPluginBuilt = BuildPlugin(lifetime, upluginFile, pluginBuildOutput,
                engineRoot, value => progressProperty.SetValue(buildProgress + value * BUILD_STEP));
            if (!isPluginBuilt)
            {
                myLogger.Warn($"Failed to build RiderLink for any available project");
                const string failedBuildText = "Failed to build RiderLink plugin";
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(failedBuildText, ContentType.Error));
                return false;
            }

            progressProperty.Value = buildProgress + BUILD_STEP;

            lifetime.ToCancellationToken().ThrowIfCancellationRequested();

            if (!PatchUpluginFileAfterInstallation(pluginBuildOutput))
            {
                const string failedToPatch = "Failed to patch RiderLink.uplugin";
                var failedPatchText = "Failed to set `EnableByDefault` to true in RiderLink.uplugin\n" +
                                      "You need to manually enable RiderLink in UnrealEditor";
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(failedToPatch, ContentType.Normal));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(failedPatchText, ContentType.Normal));
            }

            progressProperty.Value += PATCH_STEP;

            lifetime.ToCancellationToken().ThrowIfCancellationRequested();

            pluginRootFolder.CreateDirectory().DeleteChildren();
            pluginBuildOutput.Copy(pluginRootFolder);

            installDescription.IsPluginAvailable = true;
            installDescription.PluginChecksum = myPathsProvider.CurrentPluginChecksum;

            const string title = "RiderLink plugin installed";
            var text = $"RiderLink plugin was installed to: {pluginRootFolder}";

            myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(title, ContentType.Normal));
            myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(text, ContentType.Normal));

            var notification = new NotificationModel(title, text, true, RdNotificationEntryType.INFO,
                new List<NotificationHyperlink>());

            mySolution.Locks.ExecuteOrQueue(Lifetime, "UnrealLink.InstallPlugin",
                () => { myNotificationsModel.Notification(notification); });

            var cppUe4SolutionDetector = mySolution.GetComponent<CppUE4SolutionDetector>();
            bool isSln;
            using (mySolution.Locks.UsingReadLock())
            {
                isSln = cppUe4SolutionDetector.SupportRiderProjectModel != CppUE4ProjectModelSupportMode.UprojectOpened;
            }

            if (isSln)
            {
                const string refreshText = "Refreshing project files";
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(refreshText, ContentType.Normal));
                mySolution.Locks.ExecuteOrQueue(Lifetime, "Refresh projects after RiderLink installation", () => 
                    UnrealProjectsRefresher.RefreshProjects(Lifetime, myPluginDetector.UnrealVersion, mySolution, installDescription, engineRoot));
            } else {
                var actionTitle = "Update VirtualFileSystem after RiderLink installation";
                mySolution.Locks.Queue(Lifetime, actionTitle, () =>
                {
                    myLogger.Verbose(actionTitle);
                    var fileSystemModel = mySolution.GetProtocolSolution().GetFileSystemModel();
                    fileSystemModel.RefreshPaths.Start(lifetime,
                        new RdFsRefreshRequest(new List<string>() { pluginRootFolder.FullPath }, true));
                });
            }
            return true;
        }

        private bool PatchUpluginFileAfterInstallation(VirtualFileSystemPath pluginBuildOutput)
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

            Lifetime.UsingNestedAsync(async lt =>
            {
                var lifetimeDefinition = lt.CreateNested();
                var lifetime = lifetimeDefinition.Lifetime;

                lifetime.Bracket(
                    () => myUnrealHost.myModel.RiderLinkInstallationInProgress.Value = true,
                    () => myUnrealHost.myModel.RiderLinkInstallationInProgress.Value = false
                );
                var prefix = unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable ? "Updating" : "Installing";
                var header = $"{prefix} RiderLink plugin";
                var progress = new Property<double>("UnrealLink.InstallPluginProgress", 0.0);
                var task = RiderBackgroundTaskBuilder.Create()
                    .AsCancelable(() =>
                    {
                        myUnrealHost.myModel.RiderLinkInstallMessage(
                            new InstallMessage("RiderLink installation has been cancelled", ContentType.Error));
                        lifetimeDefinition.Terminate();
                    })
                    .WithHeader(header)
                    .WithProgress(progress)
                    .WithDescriptionFromProgress();
                myBackgroundTaskHost.AddNewTask(lifetime, task);
                myUnrealHost.myModel.CancelRiderLinkInstall.AdviseOnce(lifetime, unit =>
                {
                    myUnrealHost.myModel.RiderLinkInstallMessage(
                        new InstallMessage("RiderLink installation has been cancelled", ContentType.Error));
                    lifetimeDefinition.Terminate();
                });
                myUnrealHost.myModel.RiderLinkInstallPanelInit();
                await lifetime.StartBackground(() =>
                {
                    if (installPluginDescription.Location == PluginInstallLocation.Engine)
                    {
                        InstallPluginInEngine(lifetime, unrealPluginInstallInfo, progress, installPluginDescription.BuildRequired);
                    }
                    else
                    {
                        InstallPluginInGame(lifetime, unrealPluginInstallInfo, progress, installPluginDescription.BuildRequired);
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
                model.RefreshProjects.Advise(Lifetime,
                    _ => UnrealProjectsRefresher.RefreshProjects(Lifetime, myPluginDetector.UnrealVersion, mySolution, myPluginDetector.InstallInfoProperty.Value));
            });
        }

        private bool BuildPlugin(Lifetime lifetime, VirtualFileSystemPath upluginPath,
            VirtualFileSystemPath outputDir, VirtualFileSystemPath engineRoot,
            Action<double> progressPump)
        {
            var runUatName = $"RunUAT.{CmdUtils.GetPlatformCmdExtension()}";
            var pathToUat = engineRoot / "Engine" / "Build" / "BatchFiles" / runUatName;
            if (!pathToUat.ExistsFile)
            {
                myLogger.Warn($"[UnrealLink]: Failed build plugin: {runUatName} is not available");
                var text = $"{runUatName} is not available is not available at expected destination: {pathToUat}<br>";
                myUnrealHost.myModel.RiderLinkInstallMessage(
                    new InstallMessage($"Failed to build RiderLink plugin for {engineRoot}", ContentType.Error));
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(text, ContentType.Error));
                return false;
            }

            try
            {
                var pipeStreams = CreatePipeStreams("[UAT]:", progressPump);
                var startInfo = CmdUtils.GetProcessStartInfo(pipeStreams, pathToUat, null, "BuildPlugin",
                    "-Unversioned", $"-Plugin=\"{upluginPath.FullPath}\"",
                    $"-Package=\"{outputDir.FullPath}\"");

                myLogger.Info($"[UnrealLink]: Building UnrealLink plugin with: {startInfo.Arguments}");
                myLogger.Verbose("[UnrealLink]: Start building UnrealLink");

                var result = CmdUtils.RunCommandWithLock(lifetime, startInfo, myLogger);
                myLogger.Verbose("[UnrealLink]: Stop building UnrealLink");
                lifetime.ToCancellationToken().ThrowIfCancellationRequested();

                if (result != 0)
                {
                    myLogger.Warn($"[UnrealLink]: Failed to build plugin for {engineRoot}");
                    myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage("Failed to build RiderLink plugin",
                        ContentType.Error));
                    return false;
                }
            }
            catch (OperationCanceledException)
            {
                myLogger.Verbose("[UnrealLink]: Build cancelled");
                throw;
            }
            catch (Exception exception)
            {
                myLogger.Verbose("[UnrealLink]: Stop building UnrealLink");
                myLogger.Warn(exception,
                    $"[UnrealLink]: Failed to build plugin for {engineRoot}");

                myUnrealHost.myModel.RiderLinkInstallMessage(
                    new InstallMessage($"Failed to build RiderLink plugin for {engineRoot}", ContentType.Error));
                return false;
            }

            return true;
        }

        private InvokeChildProcess.PipeStreams CreatePipeStreams(string prefix, Action<double> progressPump = null)
        {
            return InvokeChildProcess.PipeStreams.Custom((chunk, isErr, logger) =>
            {
                myUnrealHost.myModel.RiderLinkInstallMessage(new InstallMessage(chunk,
                    isErr ? ContentType.Error : ContentType.Normal));
                    
                logger.Info(prefix + chunk);

                if (isErr) return;
                if (progressPump == null) return;

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
        }
    }
}