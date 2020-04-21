using System;
using System.Linq;
using JetBrains.Annotations;
using JetBrains.Application.Threading;
using JetBrains.DataFlow;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.ReSharper.Feature.Services.Cpp.Util;
using JetBrains.ReSharper.Host.Features.BackgroundTasks;
using JetBrains.ReSharper.Psi.Cpp;
using JetBrains.Rider.Model.Notifications;
using JetBrains.Util;

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
        private readonly CppUE4SolutionDetector mySolutionDetector;
        public readonly Property<UnrealPluginInstallInfo> InstallInfoProperty;

        private Version myUnrealVersion = null;
        public Version UnrealVersion => myUnrealVersion == null ? new Version(0, 0, 0) : myUnrealVersion;
        private readonly Version myMinimalSupportedVersion = new Version(4, 20, 0); 


        public UnrealPluginDetector(Lifetime lifetime, ILogger logger, UnrealHost unrealHost,
            CppUE4SolutionDetector solutionDetector, ISolution solution, NotificationsModel notificationsModel, IShellLocks locks)
        {
            myLifetime = lifetime;
            InstallInfoProperty =
                new Property<UnrealPluginInstallInfo>(myLifetime, "UnrealPlugin.InstallInfoNotification", null, true);
            myLogger = logger;
            myUnrealHost = unrealHost;
            mySolution = solution;
            mySolutionDetector = solutionDetector;
            mySolutionDetector.IsUE4Solution_Observable.Change.Advise_When(myLifetime,
                newValue => newValue == TriBool.True, isUESolution =>
                {
                    myUnrealVersion = new Version(4, mySolutionDetector.UE4Version, mySolutionDetector.UE4PatchVersion);

                    if (myUnrealVersion < myMinimalSupportedVersion)
                    {
                        var notification = new NotificationModel("Unreal Engine 4.20.0+ is required", 
                            "<html>UnrealLink supports Unreal Engine versions starting with 4.20.0<br>"+
                            "<b>WARNING: Advanced users only</b><br>"+
                            "You can manually download the latest version of plugin and build It for your version of Unreal Editor<br>" +
                            RiderContextNotificationHelper.MakeLink("https://github.com/JetBrains/UnrealLink/releases", "Download latest Unreal Editor plugin") +
                            "</html>",
                            true,
                            RdNotificationEntryType.WARN);
                        locks.ExecuteOrQueue(myLifetime, "UnrealLink.CheckSupportedVersion",() => notificationsModel.Notification(notification));
                        return;
                    }

                    var installInfo = new UnrealPluginInstallInfo();
                    var foundEnginePlugin = TryGetEnginePluginFromSolution(mySolution, installInfo);

                    var uprojectLocations = mySolution.GetAllProjects().SelectMany(project =>
                        project.GetAllProjectFiles(projectFile =>
                        {
                            var location = projectFile.Location;
                            if (location == null || !location.ExistsFile) return false;

                            return location.ExtensionNoDot == UPROJECT_FILE_FORMAT &&
                                   location.NameWithoutExtension == project.Name;
                        })).Select(file => file.Location).ToSet();

                    if (!foundEnginePlugin)
                    {
                        // All projects in the solution are bound to the same engine
                        // So take first project and use it to find Unreal Engine
                        foundEnginePlugin =
                            TryGetEnginePluginFromUproject(uprojectLocations.FirstNotNull(), installInfo);
                    }

                    if (!foundEnginePlugin)
                    {
                        // We didn't find Engine plugins, let's gather data about Project plugins
                        foreach (var uprojectLocation in uprojectLocations)
                        {
                            var projectPlugin = GetProjectPluginForUproject(uprojectLocation, installInfo);
                            installInfo.ProjectPlugins.Add(projectPlugin);
                        }
                    }

                    InstallInfoProperty.SetValue(installInfo);
                });
        }

        private UnrealPluginInstallInfo.InstallDescription GetProjectPluginForUproject(FileSystemPath uprojectLocation,
            UnrealPluginInstallInfo installInfo)
        {
            var projectRoot = uprojectLocation.Directory;
            var upluginLocation = projectRoot / ourPathToProjectPlugin;
            return GetPluginInfo(upluginLocation, uprojectLocation);
        }

        private bool TryGetEnginePluginFromUproject(FileSystemPath uprojectPath, UnrealPluginInstallInfo installInfo)
        {
            if (!uprojectPath.ExistsFile) return false;

            var unrealEngineRoot = UnrealEngineFolderFinder.FindUnrealEngineRoot(uprojectPath);
            if (unrealEngineRoot.IsEmpty) return false;

            return TryGetEnginePluginFromEngineRoot(installInfo, unrealEngineRoot);
        }

        private static bool TryGetEnginePluginFromSolution(ISolution solution, UnrealPluginInstallInfo installInfo)
        {
            var engineProject = solution.GetProjectsByName("UE4").FirstNotNull();
            if (engineProject?.ProjectFile == null) return false;

            var engineProjectFile = engineProject.ProjectFile;
            var engineRootFolder = engineProjectFile.Location.Directory.Directory.Directory;
            return TryGetEnginePluginFromEngineRoot(installInfo, engineRootFolder);
        }

        private static bool TryGetEnginePluginFromEngineRoot(UnrealPluginInstallInfo installInfo,
            FileSystemPath engineRootFolder)
        {
            var upluginFilePath = engineRootFolder / ourPathToEnginePlugin;
            installInfo.EnginePlugin = GetPluginInfo(upluginFilePath);
            return installInfo.EnginePlugin.IsPluginAvailable;
        }

        [NotNull]
        private static UnrealPluginInstallInfo.InstallDescription GetPluginInfo(
            [NotNull] FileSystemPath upluginFilePath, [CanBeNull] FileSystemPath uprojectFilePath = null)
        {
            var installDescription = new UnrealPluginInstallInfo.InstallDescription()
            {
                UnrealPluginRootFolder = upluginFilePath.Directory,
                UprojectFilePath = uprojectFilePath != null ? uprojectFilePath : FileSystemPath.Empty
            };
            if (!upluginFilePath.ExistsFile) return installDescription;

            var version = PluginPathsProvider.GetPluginVersion(upluginFilePath);
            if (version == null) return installDescription;

            installDescription.IsPluginAvailable = true;
            installDescription.PluginVersion = version;
            return installDescription;
        }
    }
}