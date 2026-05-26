using System;
using System.IO;
using System.Linq;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.Collections.Viewable;
using JetBrains.DataFlow;
using JetBrains.HabitatDetector;
using JetBrains.Lifetimes;
using JetBrains.Platform.RdFramework.Impl;
using JetBrains.ProjectModel;
using JetBrains.Rd;
using JetBrains.Rd.Base;
using JetBrains.Rd.Impl;
using JetBrains.Rd.Tasks;
using JetBrains.Util;
using JetBrains.Util.Threading;
using RiderPlugin.UnrealLink.Model;
using RiderPlugin.UnrealLink.Model.BackendUnreal;
using RiderPlugin.UnrealLink.Model.FrontendBackend;
using RiderPlugin.UnrealLink.PluginInstaller;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent(InstantiationEx.LegacyDefault)]
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
                JetPlatform.Windows => Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "..", "Local", "Jetbrains",
                    "Rider", "Unreal", "Ports"),
                JetPlatform.MacOsX => Path.Combine(Environment.GetEnvironmentVariable("HOME"), "Library",
                    "Logs", "Unreal Engine", "Ports"),
                _ => Path.Combine(Environment.GetEnvironmentVariable("HOME") ?? "", ".config",
                    "unrealEngine", "Ports")
            };
        }

        public LifetimeDefinition NestedLifetime() => myComponentLifetime.CreateNested();

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

            pluginDetector.InstallInfoProperty.ForEachValue_NotNull(myComponentLifetime, (lt, pluginInfo) =>
            {
                var portDirectoryFullPath = GetPathToPortsFolder();

                Directory.CreateDirectory(portDirectoryFullPath);

                var projects = pluginInfo.ProjectPlugins.Select(it => it.ProjectName)
                    .ToList();

                solution.Locks.Tasks.Queue(myComponentLifetime, () =>
                {
                    var watcher = new FileSystemWatcher(portDirectoryFullPath)
                    {
                        NotifyFilter = NotifyFilters.FileName,
                        Filter = "*.uproject",
                        IncludeSubdirectories = false
                    };

                    RenamedEventHandler handler = (obj, fileSystemEvent) =>
                    {
                        var path = VirtualFileSystemPath.Parse(fileSystemEvent.FullPath,
                            solution.GetInteractionContext());
                        // Skip changes to temp files
                        if (path.Name.StartsWith("~")) return;

                        if (projects.Contains(path.Name) && myComponentLifetime.IsAlive)
                        {
                            myLogger.Info(
                                $"FileSystemWatcher event {fileSystemEvent.ChangeType} found \"{path.Name}\"");
                            myLocks.ExecuteOrQueue(myComponentLifetime, "UnrealLink.CreateProtocol",
                                () => CreateProtocols(path));
                        }
                    };

                    watcher.Renamed += handler;

                    lt.Bracket(() => { }, () => { watcher.Dispose(); });

                    StartWatcher(watcher);
                }, callerInfo: CallerInfo.CreateByCurrentContext());

                foreach (var projectName in projects)
                {
                    var portFileFullPath =
                        VirtualFileSystemPath.Parse(portDirectoryFullPath, InteractionContext.SolutionContext) /
                        projectName;
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

        private void CreateProtocols(VirtualFileSystemPath portFileFullPath)
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
            var projectName = portFileFullPath.Name;

            myLogger.Info($"Creating SocketWire with port = {port}");
            var wire = new SocketWire.Client(modelLifetime, myDispatcher, port, "UnrealEditorClient");
            wire.Connected.Advise(modelLifetime,
                isConnected => myUnrealHost.PerformModelAction(riderModel =>
                    riderModel.IsConnectedToUnrealEditor.SetValue(isConnected)
                )
            );

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

        private bool ReadPortFile(VirtualFileSystemPath portFileFullPath, out string text)
        {
            text = "";
            try
            {
                text = VirtualFileSystemPath.Parse(portFileFullPath.FullPath, InteractionContext.SolutionContext)
                    .ReadAllText2().Text;
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
                riderModel.EditorId.SetValue(riderModel.EditorId.Value + 1);
            });

            var unrealModel = new RdEditorModel(lf, protocol);
            UE4Library.RegisterDeclaredTypesSerializers(protocol.Serializers);

            unrealModel.ConnectionInfo.Advise(lf, info =>
                myUnrealHost.myModel.ConnectionInfo.SetValue(info)
            );
            
            unrealModel.AllowSetForegroundWindow.SetAsync((lt, pid) =>
            {
                return myUnrealHost.PerformModelAction(riderModel =>
                    riderModel.AllowSetForegroundWindow.Start(lt, pid)) as RdTask<bool>;
            });

            unrealModel.PlayStateFromEditor.Advise(lf, myUnrealHost.myModel.PlayStateFromEditor);

            unrealModel.PlayModeFromEditor.Advise(lf, myUnrealHost.myModel.PlayModeFromEditor);
            unrealModel.PlaySettingsFromEditor.Advise(lf, myUnrealHost.myModel.PlaySettingsFromEditor);

            unrealModel.NotificationReplyFromEditor.Advise(lf, myUnrealHost.myModel.NotificationReplyFromEditor);

            unrealModel.IsGameControlModuleInitialized.Advise(lf,
                myUnrealHost.myModel.IsGameControlModuleInitialized.Set);

            unrealModel.UnrealLog.Advise(lf,
                logEvent =>
                {
                    myUnrealHost.PerformModelAction(riderModel => { OnMessageReceived(riderModel, logEvent); });
                });

            unrealModel.OnBlueprintAdded.Advise(lf, blueprintClass =>
            {
                //TO-DO
            });

            myUnrealHost.PerformModelAction(riderModel =>
            {
                riderModel.FilterLinkCandidates.SetAsync((_, candidates) =>
                    RdTask.Successful(candidates
                        .Select(request => myLinkResolver.ResolveLink(request, unrealModel.IsBlueprintPathName))
                        .AsArray()));
                riderModel.IsMethodReference.SetAsync((_, methodReference) =>
                {
                    var b = myEditorNavigator.IsMethodReference(methodReference);
                    return RdTask.Successful(b);
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
                riderModel.PlaySettingsFromRider.Advise(lf, unrealModel.PlaySettingsFromRider);
            });

            unrealModel.IsHotReloadAvailable.Advise(lf, myUnrealHost.myModel.IsHotReloadAvailable.Set);
            unrealModel.IsHotReloadCompiling.Advise(lf, myUnrealHost.myModel.IsHotReloadCompiling.Set);
            myUnrealHost.PerformModelAction(riderModel =>
            {
                riderModel.TriggerHotReload.Advise(lf, _ => unrealModel.TriggerHotReload());
                riderModel.IsConnectedToUnrealEditor.WhenFalse(lf, _ =>
                {
                    riderModel.IsHotReloadAvailable.Set(false);
                    riderModel.IsHotReloadCompiling.Set(false);
                });
            });

            // Bridge Python execution: RdRiderModel.ExecuteScript → RdEditorModel.ExecuteScript
            myUnrealHost.PerformModelAction(riderModel =>
            {
                riderModel.ExecuteScript.SetAsync((lt, request) =>
                    unrealModel.ExecuteScript.Start(lt, request).AsTask());

                riderModel.ExecuteBatchScripts.SetAsync((lt, request) =>
                    unrealModel.ExecuteBatchScripts.Start(lt, request).AsTask());

                // Bridge live asset search: RdRiderModel.SearchUnrealAssetsLive → RdEditorModel.SearchAssetsLive.
                // The Rider-model request uses plain `string?` fields; the editor-model request uses the
                // UE4Library FString marshaller. Repack at the boundary.
                riderModel.SearchUnrealAssetsLive.SetAsync(async (lt, request) =>
                {
                    static FString ToFString(string s) => s == null ? null : new FString(s);
                    var editorRequest = new AssetLiveSearchRequest(
                        ToFString(request.Query),
                        ToFString(request.BaseClass),
                        ToFString(request.PackagePath),
                        request.Limit);
                    var editorResponse = await unrealModel.SearchAssetsLive.Start(lt, editorRequest).AsTask();
                    var assets = editorResponse.Assets
                        .Select(a => new UnrealAssetLiveInfo(
                            a.AssetPath.Data,
                            a.AssetName.Data,
                            a.BaseClass?.Data,
                            a.AssetClass?.Data))
                        .ToList();
                    return new UnrealAssetLiveSearchResponse(assets);
                });

                // Bridge screenshots: RdRiderModel.TakeScreenshot → RdEditorModel.TakeScreenshot.
                // Rider-model `kind` is the string name of UE4Library.ScreenshotKind so the front-end
                // doesn't have to carry an enum across two protocol boundaries.
                riderModel.TakeScreenshot.SetAsync(async (lt, request) =>
                {
                    static FString ToFString(string s) => s == null ? null : new FString(s);
                    static ScreenshotKind ParseKind(string s) => s switch
                    {
                        "EditorWindow" => ScreenshotKind.EditorWindow,
                        "Viewport"     => ScreenshotKind.Viewport,
                        "AssetPreview" => ScreenshotKind.AssetPreview,
                        _ => throw new ArgumentException($"Unknown ScreenshotKind: {s}")
                    };
                    var editorRequest = new ScreenshotRequest(
                        ParseKind(request.Kind),
                        ToFString(request.AssetPath),
                        request.Width,
                        request.Height,
                        request.ForceLive);
                    var editorResponse = await unrealModel.TakeScreenshot.Start(lt, editorRequest).AsTask();
                    return new UnrealScreenshotResponse(
                        editorResponse.Success,
                        editorResponse.Path.Data ?? string.Empty,
                        editorResponse.Width,
                        editorResponse.Height,
                        editorResponse.SourceApi.Data ?? string.Empty,
                        editorResponse.Error.Data ?? string.Empty);
                });

                // Bridge input simulation: RdRiderModel.SimulateInput → RdEditorModel.SimulateInput.
                // The wire format is mode-dispatched with nullable per-mode fields; we just
                // repack the per-action list (plain string types) into UE4Library FString types
                // and forward the rest unchanged.
                riderModel.SimulateInput.SetAsync(async (lt, request) =>
                {
                    static FString ToFString(string s) => s == null ? null : new FString(s);

                    var actions = request.Actions
                        .Select(a => new InputActionEntry(
                            new FString(a.Type),
                            ToFString(a.Direction),
                            a.Scale, a.Yaw, a.Pitch, a.Duration))
                        .ToList();

                    Vector3 ToV3(UnrealVector3 v) => v == null ? null : new Vector3(v.X, v.Y, v.Z);

                    var editorRequest = new InputSimulationRequest(
                        new FString(request.Mode),
                        actions,
                        ToFString(request.PrimitiveCall),
                        ToFString(request.PrimitiveDirection),
                        ToV3(request.PrimitiveWorldVec),
                        request.PrimitiveScale,
                        request.PrimitiveValue,
                        request.PrimitiveDuration,
                        ToFString(request.EnhancedAssetPath),
                        ToFString(request.EnhancedValueKind),
                        request.EnhancedAxis2dX,
                        request.EnhancedAxis2dY,
                        request.EnhancedAxis1d,
                        request.EnhancedBool,
                        request.EnhancedClear);

                    var editorResponse = await unrealModel.SimulateInput.Start(lt, editorRequest).AsTask();
                    UnrealVector3 FromV3(Vector3 v) => v == null ? null : new UnrealVector3(v.X, v.Y, v.Z);
                    return new UnrealInputSimulationResponse(
                        editorResponse.Success,
                        editorResponse.Armed,
                        FromV3(editorResponse.StartLocation),
                        FromV3(editorResponse.StartVelocity),
                        editorResponse.NActions,
                        editorResponse.Error.Data ?? string.Empty);
                });

                // Bridge viewport camera: RdRiderModel.ViewportCamera → RdEditorModel.ViewportCamera.
                // The Rider model uses plain `string` for the action (mirrors TakeScreenshot's
                // `kind`) and plain UnrealVector3/UnrealRotator3 structs; we translate into the
                // UE4Library enum + FString-using request, then repack the response back to plain
                // strings on the way out.
                riderModel.ViewportCamera.SetAsync(async (lt, request) =>
                {
                    static FString ToFString(string s) => s == null ? null : new FString(s);
                    static ViewportCameraAction ParseAction(string s) => s switch
                    {
                        "Get"           => ViewportCameraAction.Get,
                        "Set"           => ViewportCameraAction.Set,
                        "Move"          => ViewportCameraAction.Move,
                        "LookAt"        => ViewportCameraAction.LookAt,
                        "FocusOnActor"  => ViewportCameraAction.FocusOnActor,
                        _ => throw new ArgumentException($"Unknown ViewportCameraAction: {s}")
                    };
                    static Vector3 ToV3(UnrealVector3 v) => v == null ? null : new Vector3(v.X, v.Y, v.Z);
                    static Rotator3 ToR3(UnrealRotator3 r) => r == null ? null : new Rotator3(r.Pitch, r.Yaw, r.Roll);

                    var editorRequest = new ViewportCameraRequest(
                        ParseAction(request.Action),
                        ToV3(request.Location),
                        ToR3(request.Rotation),
                        ToV3(request.Delta),
                        request.Relative,
                        ToR3(request.RotationDelta),
                        ToV3(request.Target),
                        ToFString(request.ActorName),
                        request.MinDistance);

                    var editorResponse = await unrealModel.ViewportCamera.Start(lt, editorRequest).AsTask();
                    return new UnrealViewportCameraResponse(
                        editorResponse.Success,
                        new UnrealVector3(editorResponse.Location.X, editorResponse.Location.Y, editorResponse.Location.Z),
                        new UnrealRotator3(editorResponse.Rotation.Pitch, editorResponse.Rotation.Yaw, editorResponse.Rotation.Roll),
                        editorResponse.ActorResolved?.Data,
                        editorResponse.Error.Data ?? string.Empty);
                });
            });

            return unrealModel;
        }

        private void OnOpenedBlueprint(RdEditorModel unrealModel, BlueprintReference blueprintReference)
        {
            unrealModel.OpenBlueprint.Fire(blueprintReference);
        }
    }
}