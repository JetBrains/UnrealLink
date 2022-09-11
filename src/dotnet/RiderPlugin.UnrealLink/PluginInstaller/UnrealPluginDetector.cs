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
        private readonly CppUE4ProjectsTracker myProjectsTracker;
        private readonly ICppUE4SolutionDetector mySolutionDetector;
        public readonly IProperty<UnrealPluginInstallInfo> InstallInfoProperty;

        public CppUE4Version UnrealVersion { get; private set; }
        private readonly CppUE4Version myMinimalSupportedVersion = new(4, 23, 0);
        private readonly CppUE4Version myNotWorkingInEngineVersion = new(5, 0, 0);

        public bool IsValidEngine() => UnrealVersion < myNotWorkingInEngineVersion || mySolutionDetector.BuiltFromSources;

        private readonly JetHashSet<string> EXCLUDED_PROJECTS = new() {"UnrealLaunchDaemon"};


        public UnrealPluginDetector(Lifetime lifetime, ILogger logger, ICppUE4SolutionDetector solutionDetector,
            IShellLocks locks, ISolutionLoadTasksScheduler scheduler, CppUE4ProjectsTracker projectsTracker)
        {
            myLifetime = lifetime;
            InstallInfoProperty =
                new Property<UnrealPluginInstallInfo>(myLifetime, "UnrealPlugin.InstallInfoNotification", null, true);
            myLogger = logger;
            myProjectsTracker = projectsTracker;
            mySolutionDetector = solutionDetector;

            mySolutionDetector.IsUnrealSolution.Change.Advise_When(myLifetime,
                newValue => newValue, _ =>
                {
                    scheduler.EnqueueTask(new SolutionLoadTask("Find installed RiderLink plugins",
                        SolutionLoadTaskKinds.Done,
                        () =>
                        {
                            myLogger.Info("[UnrealLink]: Looking for RiderLink plugins");
                            UnrealVersion = mySolutionDetector.Version;

                            if (UnrealVersion < myMinimalSupportedVersion)
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

                            var riderLinkFolders = myProjectsTracker.GetAllUPlugins().Where(pluginPath => pluginPath.NameWithoutExtension.Equals("RiderLink")).ToList();
                            var gameRoots = myProjectsTracker.GetAllUProjectRoots().Where(uprojectPath => !uprojectPath.GetChildFiles().Any(path => EXCLUDED_PROJECTS.Contains(path.NameWithoutExtension)));

                            var installInfo = new UnrealPluginInstallInfo();
                            var foundEnginePlugin = TryGetEnginePluginFromSolution(solutionDetector, installInfo);

                            // Gather data about Project plugins
                            foreach (var gameRoot in gameRoots)
                            {
                                myLogger.Info($"[UnrealLink]: Looking for plugin in {gameRoot}");
                                var upluginFolder = riderLinkFolders.Find(path => path.StartsWith(gameRoot));
                                var upluginPath = upluginFolder.IsNullOrEmpty()
                                    ? gameRoot.Combine(ourPathToProjectPlugin)
                                    : upluginFolder.CombineWithShortName(UPLUGIN_FILENAME);
                                var uprojectPath = gameRoot.GetChildFiles().Single(filePath => filePath.ExtensionNoDot.Equals(UPROJECT_FILE_FORMAT));
                                var projectPlugin = GetPluginInfo(upluginPath, uprojectPath );
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

        private bool TryGetEnginePluginFromSolution(ICppUE4SolutionDetector solutionDetector,
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
            [NotNull] VirtualFileSystemPath upluginFilePath, VirtualFileSystemPath uprojectPath)
        {
            var projectName = uprojectPath.IsNullOrEmpty() ? "<ENGINE>" : uprojectPath.Name;
            var installDescription = new UnrealPluginInstallInfo.InstallDescription()
            {
                UnrealPluginRootFolder = upluginFilePath.Directory,
                ProjectName = projectName,
                UprojectPath = uprojectPath
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