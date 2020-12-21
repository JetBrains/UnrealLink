using System;
using System.Collections.Generic;
using System.Linq;
using JetBrains.Annotations;
using JetBrains.Application.Threading;
using JetBrains.DataFlow;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Tasks;
using JetBrains.ReSharper.Feature.Services.Cpp.ProjectModel.UE4;
using JetBrains.ReSharper.Feature.Services.Cpp.Util;
using JetBrains.ReSharper.Host.Features.BackgroundTasks;
using JetBrains.ReSharper.Psi.Cpp;
using JetBrains.ReSharper.Psi.Cpp.UE4;
using JetBrains.Rider.Model.Notifications;
using JetBrains.Util;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink.PluginInstaller
{
    [SolutionComponent]
    public class UnrealPluginDetector
    {
        public static readonly string UPLUGIN_FILENAME = "RiderLink.uplugin";
        private static readonly string UPROJECT_FILE_FORMAT = "uproject";
        private static readonly RelativePath ourPathToProjectPlugin = $"Plugins/Developer/RiderLink/{UPLUGIN_FILENAME}";

        private static readonly RelativePath ourPathToEnginePlugin =
            $"Engine/Plugins/Developer/RiderLink/{UPLUGIN_FILENAME}";

        public static FileSystemPath GetPathToUpluginFile(FileSystemPath rootFolder) => rootFolder / UPLUGIN_FILENAME;

        private readonly Lifetime myLifetime;
        private readonly ILogger myLogger;
        private readonly UnrealHost myUnrealHost;
        private readonly ISolution mySolution;
        private readonly PluginPathsProvider myPluginPathsProvider;
        private readonly CppUE4SolutionDetector mySolutionDetector;
        public readonly Property<UnrealPluginInstallInfo> InstallInfoProperty;

        private Version myUnrealVersion = null;
        public Version UnrealVersion => myUnrealVersion == null ? new Version(0, 0, 0) : myUnrealVersion;
        private readonly Version myMinimalSupportedVersion = new Version(4, 20, 0);


        public UnrealPluginDetector(Lifetime lifetime, ILogger logger, UnrealHost unrealHost,
            CppUE4SolutionDetector solutionDetector, ISolution solution, NotificationsModel notificationsModel,
            IShellLocks locks, ISolutionLoadTasksScheduler scheduler, PluginPathsProvider pluginPathsProvider)
        {
            myLifetime = lifetime;
            InstallInfoProperty =
                new Property<UnrealPluginInstallInfo>(myLifetime, "UnrealPlugin.InstallInfoNotification", null, true);
            myLogger = logger;
            myUnrealHost = unrealHost;
            mySolution = solution;
            myPluginPathsProvider = pluginPathsProvider;
            mySolutionDetector = solutionDetector;

            mySolutionDetector.IsUE4Solution_Observable.Change.Advise_When(myLifetime,
                newValue => newValue == TriBool.True, isUESolution =>
                {
                    scheduler.EnqueueTask(new SolutionLoadTask("Find installed RiderLink plugins",
                        SolutionLoadTaskKinds.Done,
                        () =>
                        {
                            myLogger.Info("[UnrealLink]: Looking for RiderLink plugins");
                            myUnrealVersion = new Version(4, mySolutionDetector.UE4Version,
                                mySolutionDetector.UE4PatchVersion);

                            if (myUnrealVersion < myMinimalSupportedVersion)
                            {
                                var notification = new NotificationModel("Unreal Engine 4.20.0+ is required",
                                    "<html>UnrealLink supports Unreal Engine versions starting with 4.20.0<br>" +
                                    "<b>WARNING: Advanced users only</b><br>" +
                                    "You can manually download the latest version of plugin and build It for your version of Unreal Editor<br>" +
                                    RiderContextNotificationHelper.MakeLink(
                                        "https://github.com/JetBrains/UnrealLink/releases",
                                        "Download latest Unreal Editor plugin") +
                                    "</html>",
                                    true,
                                    RdNotificationEntryType.WARN);
                                locks.ExecuteOrQueue(myLifetime, "UnrealLink.CheckSupportedVersion",
                                    () => notificationsModel.Notification(notification));
                                return;
                            }

                            var installInfo = new UnrealPluginInstallInfo();
                            var foundEnginePlugin = TryGetEnginePluginFromSolution(solutionDetector, installInfo);
                            ISet<FileSystemPath> uprojectLocations;
                            using (solution.Locks.UsingReadLock())
                            {
                                var allProjects = mySolution.GetAllProjects();
                                if (solutionDetector.SupportRiderProjectModel == CppUE4ProjectModelSupportMode.UprojectOpened)
                                {
                                    uprojectLocations = allProjects.Where(project =>
                                    {
                                        if (project.IsMiscProjectItem() || project.IsMiscFilesProject()) return false;
                                        
                                        var location = project.Location;
                                        if (location == null) return false;

                                        // TODO: drop this ugly check after updating to net211 where Location == "path/to/game.uproject"
                                        var isUproject = location.ExistsFile && location.ExtensionNoDot == UPROJECT_FILE_FORMAT &&
                                               location.NameWithoutExtension == project.Name;
                                        return isUproject || (location / $"{location.Name}.uproject").ExistsFile;
                                    }).Select(project =>
                                    {
                                        var location = project.Location;
                                        if (location.ExistsFile) return location;
                                        return location / $"{location.Name}.uproject";
                                    }).ToSet();
                                }
                                else
                                {
                                    uprojectLocations = allProjects.SelectMany(project =>
                                        project.GetAllProjectFiles(projectFile =>
                                        {
                                            var location = projectFile.Location;
                                            if (location == null || !location.ExistsFile) return false;

                                            return location.ExtensionNoDot == UPROJECT_FILE_FORMAT &&
                                                   location.NameWithoutExtension == project.Name;
                                        })).Select(file => file.Location).ToSet();
                                }
                            }

                            myLogger.Info($"[UnrealLink]: Found {uprojectLocations.Count} uprojects");

                            if (!foundEnginePlugin && !uprojectLocations.IsEmpty())
                            {
                                // All projects in the solution are bound to the same engine
                                // So take first project and use it to find Unreal Engine
                                TryGetEnginePluginFromUproject(uprojectLocations.FirstNotNull(), installInfo);
                                foundEnginePlugin = installInfo.EnginePlugin.IsPluginAvailable;
                            }

                            // Gather data about Project plugins
                            foreach (var uprojectLocation in uprojectLocations)
                            {
                                myLogger.Info($"[UnrealLink]: Looking for plugin in {uprojectLocation}");
                                var projectPlugin = GetProjectPluginForUproject(uprojectLocation);
                                if (projectPlugin.IsPluginAvailable)
                                {
                                    myLogger.Info(
                                        $"[UnrealLink]: found plugin {projectPlugin.UnrealPluginRootFolder}");
                                }

                                installInfo.ProjectPlugins.Add(projectPlugin);
                            }

                            if (foundEnginePlugin)
                                installInfo.Location = PluginInstallLocation.Engine;
                            else if (installInfo.ProjectPlugins.Any(description => description.IsPluginAvailable))
                                installInfo.Location = PluginInstallLocation.Game;
                            else
                                installInfo.Location = PluginInstallLocation.NotInstalled;
                            InstallInfoProperty.SetValue(installInfo);
                        }));
                });
        }

        private UnrealPluginInstallInfo.InstallDescription GetProjectPluginForUproject(FileSystemPath uprojectLocation)
        {
            var projectRoot = uprojectLocation.Directory;
            var upluginLocation = projectRoot / ourPathToProjectPlugin;
            return GetPluginInfo(upluginLocation, uprojectLocation);
        }

        private bool TryGetEnginePluginFromUproject(FileSystemPath uprojectPath, UnrealPluginInstallInfo installInfo)
        {
            if (!uprojectPath.ExistsFile) return false;

            var unrealEngineRoot = CppUE4FolderFinder.FindUnrealEngineRoot(uprojectPath);
            if (unrealEngineRoot.IsEmpty) return false;

            return TryGetEnginePluginFromEngineRoot(installInfo, unrealEngineRoot);
        }

        private bool TryGetEnginePluginFromSolution(CppUE4SolutionDetector solutionDetector, UnrealPluginInstallInfo installInfo)
        {
            var engineRootFolder = solutionDetector.UE4SourcesPath.Directory;
            return TryGetEnginePluginFromEngineRoot(installInfo, engineRootFolder);
        }

        private bool TryGetEnginePluginFromEngineRoot(UnrealPluginInstallInfo installInfo,
            FileSystemPath engineRootFolder)
        {
            var upluginFilePath = engineRootFolder / ourPathToEnginePlugin;
            installInfo.EnginePlugin = GetPluginInfo(upluginFilePath);
            if (installInfo.EnginePlugin.IsPluginAvailable)
            {
                myLogger.Info($"[UnrealLink]: found plugin {installInfo.EnginePlugin.UnrealPluginRootFolder}");
            }

            return installInfo.EnginePlugin.IsPluginAvailable;
        }

        [NotNull]
        private UnrealPluginInstallInfo.InstallDescription GetPluginInfo(
            [NotNull] FileSystemPath upluginFilePath, [CanBeNull] FileSystemPath uprojectFilePath = null)
        {
            var installDescription = new UnrealPluginInstallInfo.InstallDescription()
            {
                UnrealPluginRootFolder = upluginFilePath.Directory,
                UprojectFilePath = uprojectFilePath != null ? uprojectFilePath : FileSystemPath.Empty
            };
            if (!upluginFilePath.ExistsFile) return installDescription;

            var version = myPluginPathsProvider.GetPluginVersion(upluginFilePath);
            if (version == null) return installDescription;

            installDescription.IsPluginAvailable = true;
            installDescription.PluginVersion = version;
            return installDescription;
        }
    }
}