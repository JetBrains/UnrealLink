using System;
using System.Collections.Generic;
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
using JetBrains.ReSharper.Feature.Services.Cpp.ProjectModel.UE4;
using JetBrains.ReSharper.Host.Features.BackgroundTasks;
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
            myLifetime = lifetime;
            myLogger = logger;
            myPathsProvider = pathsProvider;
            mySolution = solution;
            myUnrealHost = unrealHost;
            myNotificationsModel = notificationsModel;
            myBackgroundTaskHost = backgroundTaskHost;
            myBoundSettingsStore =
                settingsStore.BindToContextLive(myLifetime, ContextRange.Smart(solution.ToDataContext()));
            myPluginDetector = pluginDetector;

            myPluginDetector.InstallInfoProperty.Change.Advise_NewNotNull(myLifetime, installInfo =>
            {
                mySolution.Locks.ExecuteOrQueueReadLockEx(myLifetime,
                    "UnrealPluginInstaller.CheckAllProjectsIfAutoInstallEnabled",
                    () =>
                    {
                        var unrealPluginInstallInfo = installInfo.New;

                        if (!myBoundSettingsStore.GetValue((UnrealLinkSettings s) => s.InstallRiderLinkPlugin))
                        {
                            if (unrealPluginInstallInfo.EnginePlugin.PluginVersion !=
                                myPathsProvider.CurrentPluginVersion)
                            {
                                myLogger.Warn("[UnrealLink]: Plugin is out of sync");
                                myUnrealHost.PerformModelAction(model => model.OnEditorModelOutOfSync());
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
            mySolution.Locks.ExecuteOrQueueReadLockEx(myLifetime,
                "UnrealPluginInstaller.InstallPluginIfRequired",
                () => InstallPluginIfRequired(unrealPluginInstallInfo));
        }

        private sealed class DeleteTempFolders : IDisposable
        {
            private readonly FileSystemPath myTempFolder;

            public DeleteTempFolders(FileSystemPath tempFolder)
            {
                myTempFolder = tempFolder;
            }

            public void Dispose()
            {
                myTempFolder.Delete();
            }
        }

        private void InstallPluginIfRequired(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            if (unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable &&
                unrealPluginInstallInfo.EnginePlugin.PluginVersion == myPathsProvider.CurrentPluginVersion) return;

            Lifetime.Using(lifetime =>
            {
                var prefix = unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable ? "Updating" : "Installing";
                var header = $"{prefix} RiderLink plugin";
                var task = RiderBackgroundTaskBuilder.Create()
                    .AsNonCancelable()
                    .AsIndeterminate()
                    .WithHeader(header);
                myBackgroundTaskHost.AddNewTask(lifetime, task);
                InstallPlugin(unrealPluginInstallInfo);
            });
        }

        private void InstallPlugin(UnrealPluginInstallInfo unrealPluginInstallInfo)
        {
            var backupDir = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
            using var deleteTempFolders = new DeleteTempFolders(backupDir.Directory);

            var enginePluginRootFolder = unrealPluginInstallInfo.EnginePlugin.UnrealPluginRootFolder;
            if (enginePluginRootFolder.ExistsDirectory)
            {
                try
                {
                    enginePluginRootFolder.Copy(backupDir);
                }
                catch (Exception exception)
                {
                    myLogger.Error(exception, $"[UnrealLink]: Failed to backup {enginePluginRootFolder}");
                    const string backupFailTitle = "Failed to backup old RiderLink plugin";
                    var backupFailText = $"<html>Please, manually remove<br>" +
                                         $"<b>{enginePluginRootFolder.FullPath}\"</b><br>" +
                                         "And try installing <b>RiderLink</b> again</html>";
                    Notify(backupFailTitle, backupFailText, RdNotificationEntryType.WARN);
                    return;
                }

                try
                {
                    unrealPluginInstallInfo.EnginePlugin.UnrealPluginRootFolder.Delete();
                }
                catch (Exception exception)
                {
                    myLogger.Error(exception, $"[UnrealLink]: Failed to delete {enginePluginRootFolder}");
                    const string deleteOldFailTitle = "Failed to delete old RiderLink plugin";
                    var deleteOldFailText = $"<html>Please, manually remove<br>" +
                                            "<a href=\"{unrealPluginInstallInfo.EnginePlugin.UnrealPluginRootFolder}\">Old plugin</a><br>" +
                                            "And try installing <b>RiderLink</b> again</html>";
                    Notify(deleteOldFailTitle, deleteOldFailText, RdNotificationEntryType.WARN);
                    return;
                }
            }

            var editorPluginPathFile = myPathsProvider.PathToPackedPlugin;
            var pluginTmpDir = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);
            try
            {
                ZipFile.ExtractToDirectory(editorPluginPathFile.FullPath, pluginTmpDir.FullPath);
            }
            catch (Exception exception)
            {
                myLogger.Error(exception, $"[UnrealLink]: Couldn't extract {editorPluginPathFile} to {pluginTmpDir}");

                const string unzipFailTitle = "Failed to unzip new RiderLink plugin";
                var unzipFailText =
                    $"<html>Failed to unzip <a href=\"{editorPluginPathFile.FullPath}\">new version of RiderLink</a> to user folder<br>" +
                    "Try restarting Rider in administrative mode</html>";
                Notify(unzipFailTitle, unzipFailText, RdNotificationEntryType.WARN);
                return;
            }

            var upluginFile = UnrealPluginDetector.GetPathToUpluginFile(pluginTmpDir);
            if (!PatchTypeOfUpluginFile(upluginFile, myLogger, myPluginDetector.UnrealVersion))
            {
                pluginTmpDir.Delete();
            }

            var pluginBuildOutput = FileSystemDefinition.CreateTemporaryDirectory(null, TMP_PREFIX);

            bool failedToBuild = true;
            foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
            {
                if (BuildPlugin(upluginFile,
                    pluginBuildOutput,
                    installDescription.UprojectFilePath))
                {
                    failedToBuild = false;
                    break;
                }
            }

            if (failedToBuild)
            {
                myLogger.Error($"Failed to build RiderLink for any available project");
                const string failedBuildTitle = "Failed to build RiderLink plugin";
                var failedBuildText = "<html>" +
                                      "Check build logs for more info<b>" +
                                      "Help > Diagnostic Tools > Show Log in Explorer" +
                                      "</html>";
                Notify(failedBuildTitle, failedBuildText, RdNotificationEntryType.ERROR);
                return;
            }

            enginePluginRootFolder.CreateDirectory().DeleteChildren();
            pluginBuildOutput.Copy(enginePluginRootFolder);

            foreach (var installDescription in unrealPluginInstallInfo.ProjectPlugins)
            {
                installDescription.UnrealPluginRootFolder.Delete();
            }

            unrealPluginInstallInfo.EnginePlugin.IsPluginAvailable = true;
            unrealPluginInstallInfo.EnginePlugin.PluginVersion = myPathsProvider.CurrentPluginVersion;

            const string title = "Unreal Editor plugin installed";
            var text = "<html>Unreal Editor plugin was installed to:<br>" +
                       $"<b>{enginePluginRootFolder}<b>" +
                       "</html>";

            Notify(title, text, RdNotificationEntryType.INFO);

            RegenerateProjectFiles(unrealPluginInstallInfo.ProjectPlugins.FirstNotNull()?.UprojectFilePath);
        }

        private void Notify(string title, string text, RdNotificationEntryType verbosity)
        {
            var notification = new NotificationModel(title, text, true, verbosity);

            mySolution.Locks.ExecuteOrQueue(myLifetime, "UnrealLink.InstallPlugin",
                () => { myNotificationsModel.Notification(notification); });
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
                newValue => newValue, args => { InstallPluginIfInfoAvailable(); });
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
                model.InstallEditorPlugin.AdviseNotNull(myLifetime, unit => { InstallPluginIfInfoAvailable(); });
                model.EnableAutoupdatePlugin.AdviseNotNull(myLifetime,
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

                mySolution.Locks.ExecuteOrQueue(myLifetime, "UnrealLink.RefreshProject",
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

            mySolution.Locks.ExecuteOrQueue(myLifetime, "UnrealLink.RefreshProject",
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

                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    GetPathToCmd(),
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
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    GetPathToCmd(),
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
                ErrorLevelException.ThrowIfNonZero(InvokeChildProcess.InvokeChildProcessIntoLogger(
                    GetPathToCmd(),
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
            var installedbuildTxt = engineRoot / "Engine" / "Build" / "InstalledBuild.txt";
            var isInstalledBuild = installedbuildTxt.ExistsFile;
            return isInstalledBuild;
        }

        private bool BuildPlugin(FileSystemPath upluginPath, FileSystemPath outputDir, FileSystemPath uprojectFile)
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

            try
            {
                List<string> stdOut = new List<string>();
                List<string> stdErr = new List<string>();
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
                });
                myLogger.Info($"[UnrealLink]: Building UnrealLink plugin with: {commandLine}");
                var pathToCmdExe = GetPathToCmd();

                myLogger.Verbose("[UnrealLink]: Start building UnrealLink");
                var result = InvokeChildProcess.InvokeSync(pathToCmdExe, hackCmd,
                    pipeStreams,TimeSpan.FromMinutes(1), null, null, null, myLogger);
                myLogger.Verbose(stdOut.Join("\n"));
                myLogger.Verbose("[UnrealLink]: Stop building UnrealLink");
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
                myLogger.Error(exception,
                    $"[UnrealLink]: Failed to build plugin for {uprojectFile}");
                return false;
            }

            return true;
        }

        private static FileSystemPath GetPathToCmd()
        {
            var comspec = Environment.GetEnvironmentVariable("COMSPEC");
            var pathToCmd = FileSystemPath.Parse(comspec);
            return pathToCmd;
        }
    }
}