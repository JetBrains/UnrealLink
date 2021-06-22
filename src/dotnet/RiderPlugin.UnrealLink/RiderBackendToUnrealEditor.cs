using System;
using System.IO;
using System.Linq;
using JetBrains.Application.Threading;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Platform.RdFramework.Impl;
using JetBrains.ProjectModel;
using JetBrains.Rd;
using JetBrains.Rd.Base;
using JetBrains.Rd.Impl;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Features.XamlRendererHost.Preview;
using JetBrains.Util;
using RiderPlugin.UnrealLink.Model;
using RiderPlugin.UnrealLink.Model.BackendUnreal;
using RiderPlugin.UnrealLink.Model.FrontendBackend;
using RiderPlugin.UnrealLink.PluginInstaller;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent]
    public class RiderBackendToUnrealEditor
    {
        public RdEditorModel EditorModel { get; private set; }

        private readonly RdDispatcher myDispatcher;
        private readonly ILogger myLogger;
        private readonly UnrealHost myUnrealHost;
        private readonly UnrealLinkResolver myLinkResolver;
        private readonly EditorNavigator myEditorNavigator;
        private Lifetime myComponentLifetime;
        private readonly IShellLocks myLocks;
        private SequentialLifetimes myConnectionLifetimeProducer;

        private static string GetPathToPortsFolder()
        {
            return PlatformUtil.RuntimePlatform switch
            {
                PlatformUtil.Platform.Windows => Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "..", "Local", "Jetbrains",
                    "Rider", "Unreal", "Ports"),
                PlatformUtil.Platform.MacOsX => Path.Combine(Environment.GetEnvironmentVariable("HOME"), "Library",
                    "Logs", "Unreal Engine", "Ports"),
                _ => Path.Combine(Environment.GetEnvironmentVariable("HOME") ?? "", ".config",
                    "unrealEngine", "Ports")
            };
        }

        public RiderBackendToUnrealEditor(Lifetime lifetime, IShellLocks locks, RdDispatcher dispatcher, ILogger logger,
            UnrealHost unrealHost, UnrealLinkResolver linkResolver, EditorNavigator editorNavigator,
            UnrealPluginDetector pluginDetector, ISolution solution)
        {
            myComponentLifetime = lifetime;
            myLocks = locks;
            myConnectionLifetimeProducer = new SequentialLifetimes(lifetime);
            myDispatcher = dispatcher;
            myLogger = logger;
            myUnrealHost = unrealHost;
            myLinkResolver = linkResolver;
            myEditorNavigator = editorNavigator;

            myLogger.Info("RiderBackendToUnrealEditor building started");

            pluginDetector.InstallInfoProperty.View(myComponentLifetime, (lt, pluginInfo) =>
            {
                if (pluginInfo == null) return;

                var portDirectoryFullPath = GetPathToPortsFolder();

                Directory.CreateDirectory(portDirectoryFullPath);

                var projects = pluginInfo.ProjectPlugins.Select(it => it.UprojectFilePath.Name)
                    .ToList();

                solution.Locks.Tasks.Queue(myComponentLifetime, () =>
                {
                    var watcher = new FileSystemWatcher(portDirectoryFullPath)
                    {
                        NotifyFilter = NotifyFilters.LastWrite
                    };

                    FileSystemEventHandler handler = (obj, fileSystemEvent) =>
                    {
                        var path = FileSystemPath.Parse(fileSystemEvent.FullPath);
                        if (projects.Contains(path.Name) && myComponentLifetime.IsAlive)
                        {
                            myLogger.Info(
                                $"FileSystemWatcher event {fileSystemEvent.ChangeType} found \"{path.Name}\"");
                            myLocks.ExecuteOrQueue(myComponentLifetime, "UnrealLink.CreateProtocol",
                                () => CreateProtocols(path));
                        }
                    };

                    watcher.Changed += handler;
                    watcher.Created += handler;

                    lt.Bracket(() => { }, () => { watcher.Dispose(); });

                    StartWatcher(watcher);
                });

                foreach (var projectName in projects)
                {
                    var portFileFullPath = FileSystemPath.Parse(portDirectoryFullPath) / projectName;
                    myLocks.ExecuteOrQueue(myComponentLifetime, "UnrealLink.CreateProtocol",
                        () => CreateProtocols(portFileFullPath));
                }
            });

            myLogger.Info("RiderBackendToUnrealEditor building finished");
        }

        private static void StartWatcher(FileSystemWatcher watcher)
        {
            watcher.EnableRaisingEvents = true;
        }

        private void CreateProtocols(FileSystemPath portFileFullPath)
        {
            myLogger.Info($"Trying to read port file {portFileFullPath}");
            if (!portFileFullPath.ExistsFile) return;

            if (!ReadPortFile(portFileFullPath, out var text))
            {
                return;
            }

            if (!int.TryParse(text, out var port))
            {
                myLogger.Error($"[UnrealLink]: Couldn't parse port from file:{portFileFullPath}, text:{text}");
                return;
            }

            var modelLifetime = myConnectionLifetimeProducer.Next();

            myLogger.Info("Creating SocketWire with port = {0}", port);
            var wire = new SocketWire.Client(modelLifetime, myDispatcher, port, "UnrealEditorClient");
            wire.Connected.Advise(modelLifetime, isConnected => myUnrealHost.PerformModelAction(riderModel =>
                riderModel.IsConnectedToUnrealEditor.SetValue(isConnected)));

            var protocol = new Protocol("UnrealEditorPlugin", new Serializers(null, null),
                new Identities(IdKind.Client), myDispatcher, wire, modelLifetime);

            wire.Connected.View(modelLifetime, (lf, isConnected) =>
            {
                RdEditorModel model;
                if (isConnected)
                {
                    myLogger.Info("Wire connected");
                    model = ResetModel(lf, protocol);
                }
                else
                {
                    myLogger.Info("Wire disconnected");
                    model = null;
                }
                
                EditorModel = model;
            });
        }

        private bool ReadPortFile(FileSystemPath portFileFullPath, out string text)
        {
            text = "";
            try
            {
                text = FileSystemPath.Parse(portFileFullPath.FullPath).ReadAllText2().Text;
            }
            catch (Exception exception)
            {
                myLogger.Warn(
                    $"[UnrealLink]: Failed to read connection port from {portFileFullPath}, reason: {exception.Message}");
                return false;
            }

            return true;
        }

        void OnMessageReceived(RdRiderModel riderModel, UnrealLogEvent message)
        {
            riderModel.UnrealLog.Fire(message);
        }

        private RdEditorModel ResetModel(Lifetime lf, IProtocol protocol)
        {
            myUnrealHost.PerformModelAction(riderModel =>
            {
                UE4Library.RegisterDeclaredTypesSerializers(riderModel.SerializationContext.Serializers);
                riderModel.EditorId.SetValue(riderModel.EditorId.Value + 1);
            });

            var unrealModel = new RdEditorModel(lf, protocol);
            UE4Library.RegisterDeclaredTypesSerializers(unrealModel.SerializationContext.Serializers);

            unrealModel.AllowSetForegroundWindow.Set((lt, pid) =>
            {
                return myUnrealHost.PerformModelAction(riderModel =>
                    riderModel.AllowSetForegroundWindow.Start(lt, pid)) as RdTask<bool>;
            });

            unrealModel.PlayStateFromEditor.Advise(lf, myUnrealHost.myModel.PlayStateFromEditor);

            unrealModel.PlayModeFromEditor.Advise(lf, myUnrealHost.myModel.PlayModeFromEditor);
            
            unrealModel.NotificationReplyFromEditor.Advise(lf, myUnrealHost.myModel.NotificationReplyFromEditor);
            
            unrealModel.IsGameControlModuleInitialized.Advise(lf, myUnrealHost.myModel.IsGameControlModuleInitialized.Set);

            unrealModel.UnrealLog.Advise(lf,
                logEvent =>
                {
                    myUnrealHost.PerformModelAction(riderModel => { OnMessageReceived(riderModel, logEvent); });
                });

            unrealModel.OnBlueprintAdded.Advise(lf, blueprintClass =>
            {
                //todo
            });

            myUnrealHost.PerformModelAction(riderModel =>
            {
                riderModel.FilterLinkCandidates.Set((lifetime, candidates) =>
                    RdTask<ILinkResponse[]>.Successful(candidates
                        .Select(request => myLinkResolver.ResolveLink(request, unrealModel.IsBlueprintPathName))
                        .AsArray()));
                riderModel.IsMethodReference.Set((lifetime, methodReference) =>
                {
                    var b = myEditorNavigator.IsMethodReference(methodReference);
                    return RdTask<bool>.Successful(b);
                });
                riderModel.OpenBlueprint.Advise(lf, blueprintReference =>
                    OnOpenedBlueprint(unrealModel, blueprintReference));

                riderModel.NavigateToClass.Advise(lf,
                    uClass => myEditorNavigator.NavigateToClass(uClass));

                riderModel.NavigateToMethod.Advise(lf,
                    methodReference => myEditorNavigator.NavigateToMethod(methodReference));

                riderModel.RequestPlayFromRider.Advise(lf, unrealModel.RequestPlayFromRider);
                riderModel.RequestPauseFromRider.Advise(lf, unrealModel.RequestPauseFromRider);
                riderModel.RequestResumeFromRider.Advise(lf, unrealModel.RequestResumeFromRider);
                riderModel.RequestStopFromRider.Advise(lf, unrealModel.RequestStopFromRider);
                riderModel.RequestFrameSkipFromRider.Advise(lf, unrealModel.RequestFrameSkipFromRider);
                riderModel.PlayModeFromRider.Advise(lf, unrealModel.PlayModeFromRider);
            });
            
            return unrealModel;
        }

        private void OnOpenedBlueprint(RdEditorModel unrealModel, BlueprintReference blueprintReference)
        {
            unrealModel.OpenBlueprint.Fire(blueprintReference);
        }
    }
}