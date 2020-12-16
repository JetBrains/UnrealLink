using System;
using System.IO;
using System.Linq;
using JetBrains.Application.Threading;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
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
        private readonly IScheduler myDispatcher;
        private readonly ILogger myLogger;
        private readonly UnrealHost myUnrealHost;
        private readonly UnrealLinkResolver myLinkResolver;
        private readonly EditorNavigator myEditorNavigator;
        private readonly ViewableProperty<RdEditorModel> myEditorModel = new ViewableProperty<RdEditorModel>(null);

        private bool PlayModeFromUnreal = false;
        private bool PlayModeFromRider = false;
        private Lifetime myComponentLifetime;
        private readonly IShellLocks myLocks;
        private SequentialLifetimes myConnectionLifetimeProducer;

        public RiderBackendToUnrealEditor(Lifetime lifetime, IShellLocks locks, IScheduler dispatcher, ILogger logger,
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

                var portDirectoryFullPath = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "..",
                    "Local", "Jetbrains", "Rider", "Unreal", "Ports");

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

            var protocol = new Protocol("UnrealEditorPlugin", new Serializers(modelLifetime, null, null),
                new Identities(IdKind.Client), myDispatcher, wire, modelLifetime);

            wire.Connected.WhenTrue(modelLifetime, lifetime =>
            {
                myLogger.Info("Wire connected");
                ResetModel(lifetime, protocol);
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

        private void ResetModel(Lifetime lf, IProtocol protocol)
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
            myUnrealHost.myModel.PlayStateFromRider.Advise(lf, unrealModel.PlayStateFromRider);

            unrealModel.UnrealLog.Advise(lf,
                logEvent =>
                {
                    myUnrealHost.PerformModelAction(riderModel => { OnMessageReceived(riderModel, logEvent); });
                });

            unrealModel.OnBlueprintAdded.Advise(lf, blueprintClass =>
            {
                //todo
            });

            unrealModel.PlayMode.Advise(lf, val =>
            {
                myUnrealHost.PerformModelAction(riderModel =>
                {
                    if (PlayModeFromRider)
                        return;
                    try
                    {
                        PlayModeFromUnreal = true;
                        riderModel.PlayMode.Set(val);
                    }
                    finally
                    {
                        PlayModeFromUnreal = false;
                    }
                });
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

                riderModel.PlayMode.Advise(lf, val =>
                {
                    if (PlayModeFromUnreal)
                        return;
                    try
                    {
                        PlayModeFromRider = true;
                        unrealModel.PlayMode.Set(val);
                    }
                    finally
                    {
                        PlayModeFromRider = false;
                    }
                });
                riderModel.FrameSkip.Advise(lf, unrealModel.FrameSkip);
            });

            if (myComponentLifetime.IsAlive)
                myLocks.ExecuteOrQueueEx(myComponentLifetime, "setModel",
                    () => { myEditorModel.SetValue(unrealModel); });
        }

        private void OnOpenedBlueprint(RdEditorModel unrealModel, BlueprintReference blueprintReference)
        {
            unrealModel.OpenBlueprint.Fire(blueprintReference);
        }

        public RdEditorModel GetCurrentEditorModel()
        {
            return myEditorModel.Value;
        }
    }
}