using System.Collections.Generic;
using System.Linq;
using JetBrains.Annotations;
using JetBrains.Application.Threading;
using JetBrains.DataFlow;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Tasks;
using JetBrains.RdBackend.Common.Features.BackgroundTasks;
using JetBrains.ReSharper.Feature.Services.Cpp.ProjectModel.UE4;
using JetBrains.ReSharper.Feature.Services.Cpp.Util;
using JetBrains.ReSharper.Psi.Cpp;
using JetBrains.ReSharper.Resources.Shell;
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
        public static readonly string CHEKSUM_ENTRY_PATH = "Resources/checksum";
        private static readonly string UPROJECT_FILE_FORMAT = "uproject";
        private readonly RelativePath ourPathToProjectPlugin = $"Plugins/Developer/RiderLink/{UPLUGIN_FILENAME}";

        private readonly RelativePath ourPathToEnginePlugin =
            $"Engine/Plugins/Developer/RiderLink/{UPLUGIN_FILENAME}";

        public static VirtualFileSystemPath GetPathToUpluginFile(VirtualFileSystemPath rootFolder) => rootFolder / UPLUGIN_FILENAME;

        private readonly Lifetime myLifetime;
        private readonly ILogger myLogger;
        private readonly ISolution mySolution;
        private readonly CppUE4SolutionDetector mySolutionDetector;
        public readonly IProperty<UnrealPluginInstallInfo> InstallInfoProperty;

        private CppUE4Version myUnrealVersion;
        private readonly CppUE4Version myMinimalSupportedVersion = new(4, 23, 0);
        private readonly CppUE4Version myNotWorkingInEngineVersion = new(5, 0, 0);

        public bool IsValidEngine() => myUnrealVersion != myNotWorkingInEngineVersion || mySolutionDetector.BuiltFromSources;

        private readonly JetHashSet<string> EXCLUDED_PROJECTS = new() {"UnrealLaunchDaemon"};


        public UnrealPluginDetector(Lifetime lifetime, ILogger logger,
            CppUE4SolutionDetector solutionDetector, ISolution solution,
            IShellLocks locks, ISolutionLoadTasksScheduler scheduler)
        {
            myLifetime = lifetime;
            InstallInfoProperty =
                new Property<UnrealPluginInstallInfo>(myLifetime, "UnrealPlugin.InstallInfoNotification", null, true);
            myLogger = logger;
            mySolution = solution;
            mySolutionDetector = solutionDetector;

            mySolutionDetector.IsUE4Solution_Observable.Change.Advise_When(myLifetime,
                newValue => newValue == TriBool.True, _ =>
                {
                    scheduler.EnqueueTask(new SolutionLoadTask("Find installed RiderLink plugins",
                        SolutionLoadTaskKinds.Done,
                        () =>
                        {
                            myLogger.Info("[UnrealLink]: Looking for RiderLink plugins");
                            myUnrealVersion = mySolutionDetector.Version;

                            if (myUnrealVersion < myMinimalSupportedVersion)
                            {
                                locks.ExecuteOrQueue(myLifetime, "UnrealLink.CheckSupportedVersion",
                                    () =>
                                    {
                                        var notification =
                                                new NotificationModel(
                                                    $"Unreal Engine {myMinimalSupportedVersion}+ is required", 
                                            $"<html>UnrealLink supports Unreal Engine versions starting with {myMinimalSupportedVersion}<br>" +
                                            "<b>WARNING: Advanced users only</b><br>" +
                                            "You can manually download the latest version of plugin and build It for your version of Unreal Editor<br>" +
                                            RiderContextNotificationHelper.MakeLink(
                                                "https://github.com/JetBrains/UnrealLink/releases/latest",
                                                "Download latest Unreal Editor plugin") +
                                            "</html>",
                                            true,
                                            RdNotificationEntryType.WARN,
                                            new List<NotificationHyperlink>());
                                        var notificationsModel = Shell.Instance.GetComponent<NotificationsModel>();
                                        notificationsModel.Notification(notification);
                                    });
                                return;
                            }

                            var installInfo = new UnrealPluginInstallInfo();
                            var foundEnginePlugin = TryGetEnginePluginFromSolution(solutionDetector, installInfo);
                            ISet<VirtualFileSystemPath> uprojectLocations;
                            using (solution.Locks.UsingReadLock())
                            {
                                var allProjects = mySolution.GetAllProjects();
                                if (solutionDetector.SupportRiderProjectModel ==
                                    CppUE4ProjectModelSupportMode.UprojectOpened)
                                {
                                    uprojectLocations = allProjects.Where(project =>
                                    {
                                        if (project.IsMiscProjectItem() || project.IsMiscFilesProject()) return false;

                                        var location = project.ProjectFileLocation;
                                        if (location == null) return false;

                                        if (EXCLUDED_PROJECTS.Contains(location.NameWithoutExtension)) return false;

                                        return location.ExtensionNoDot == UPROJECT_FILE_FORMAT &&
                                               location.NameWithoutExtension == project.Name;
                                    }).Select(project => project.ProjectFileLocation).ToSet();
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
                                foundEnginePlugin = TryGetEnginePluginFromUproject(uprojectLocations.FirstNotNull(), installInfo);
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

        private UnrealPluginInstallInfo.InstallDescription GetProjectPluginForUproject(VirtualFileSystemPath uprojectLocation)
        {
            var projectRoot = uprojectLocation.Directory;
            var upluginLocation = projectRoot / ourPathToProjectPlugin;
            return GetPluginInfo(upluginLocation, uprojectLocation );
        }

        private bool TryGetEnginePluginFromUproject(VirtualFileSystemPath uprojectPath, UnrealPluginInstallInfo installInfo)
        {
            if (!uprojectPath.ExistsFile) return false;

            var unrealEngineRoot = CppUE4FolderFinder.FindUnrealEngineRoot(uprojectPath);
            if (unrealEngineRoot.IsEmpty) return false;

            return TryGetEnginePluginFromEngineRoot(installInfo, unrealEngineRoot);
        }

        private bool TryGetEnginePluginFromSolution(CppUE4SolutionDetector solutionDetector,
            UnrealPluginInstallInfo installInfo)
        {
            var engineRootFolder = solutionDetector.UE4SourcesPath.Directory;
            return TryGetEnginePluginFromEngineRoot(installInfo, engineRootFolder);
        }

        private bool TryGetEnginePluginFromEngineRoot(UnrealPluginInstallInfo installInfo,
            VirtualFileSystemPath engineRootFolder)
        {
            var upluginFilePath = engineRootFolder / ourPathToEnginePlugin;
            installInfo.EnginePlugin = GetPluginInfo(upluginFilePath, VirtualFileSystemPath.GetEmptyPathFor(InteractionContext.SolutionContext));
            if (installInfo.EnginePlugin.IsPluginAvailable)
            {
                myLogger.Info($"[UnrealLink]: found plugin {installInfo.EnginePlugin.UnrealPluginRootFolder}");
            }

            installInfo.EngineRoot = engineRootFolder;

            return installInfo.EnginePlugin.IsPluginAvailable;
        }

        [NotNull]
        private UnrealPluginInstallInfo.InstallDescription GetPluginInfo(
            [NotNull] VirtualFileSystemPath upluginFilePath, VirtualFileSystemPath UprojectPath)
        {
            var ProjectName = UprojectPath.IsNullOrEmpty() ? "<ENGINE>" : UprojectPath.Name;
            var installDescription = new UnrealPluginInstallInfo.InstallDescription()
            {
                UnrealPluginRootFolder = upluginFilePath.Directory,
                ProjectName = ProjectName,
                UprojectPath = UprojectPath
            };
            if (!upluginFilePath.ExistsFile) return installDescription;

            var pluginPathsProvider = Shell.Instance.GetComponent<PluginPathsProvider>();
            var pluginChecksumFilePath = upluginFilePath.Directory.Combine("Resources").Combine("checksum");
            var pluginChecksum = pluginPathsProvider.GetPluginChecksum(pluginChecksumFilePath);
            if (pluginChecksum == null) return installDescription;

            installDescription.IsPluginAvailable = true;
            installDescription.PluginChecksum = pluginChecksum;
            return installDescription;
        }
    }
}