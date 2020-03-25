using System;
using System.IO;
using System.Linq;
using JetBrains.Collections.Viewable;
using JetBrains.DataFlow;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.Platform.Unreal.EditorPluginModel;
using JetBrains.ProjectModel;
using JetBrains.Rd;
using JetBrains.Rd.Base;
using JetBrains.Rd.Impl;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Features.XamlRendererHost.Preview;
using JetBrains.ReSharper.Psi.Cpp.Caches;
using JetBrains.Rider.Model;
using JetBrains.Unreal.Lib;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent]
    public class RiderBackendToUnrealEditor
    {
        private readonly IScheduler myDispatcher;
        private readonly ISolution mySolution;
        private readonly ILogger myLogger;
        private readonly UnrealHost myUnrealHost;
        private readonly UnrealLinkResolver myLinkResolver;
        private readonly EditorNavigator myEditorNavigator;
        private readonly CppGlobalSymbolCache myCppSymbolNameCache;
        private readonly IProperty<RdEditorModel> myEditorModel;

        private const string PortFileName = "UnrealProtocolPort.txt";
        private const string ClosedFileExtension = ".closed";

        private bool PlayedFromUnreal = false;
        private bool PlayedFromRider = false;
        private bool PlayModeFromUnreal = false;
        private bool PlayModeFromRider = false;

        public RiderBackendToUnrealEditor(Lifetime lifetime, IScheduler dispatcher, ISolution solution, ILogger logger,
            UnrealHost unrealHost, UnrealLinkResolver linkResolver, EditorNavigator editorNavigator)
        {
            myDispatcher = dispatcher;
            mySolution = solution;
            myLogger = logger;
            myUnrealHost = unrealHost;
            myLinkResolver = linkResolver;
            myEditorNavigator = editorNavigator;

            myLogger.Info("RiderBackendToUnrealEditor building started");

            var modelLifetimeDefinition = lifetime.CreateNested();
            var modelLifetime = modelLifetimeDefinition.Lifetime;
            myEditorModel = new Property<RdEditorModel>(modelLifetime, "RiderTounrealModel");


            var projectName = mySolution.Name;
            var portDirectoryFullPath = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "..",
                "Local", "Jetbrains", "Rider", "Unreal", projectName, "Ports");

            Directory.CreateDirectory(portDirectoryFullPath);

            var watcher = new FileSystemWatcher(portDirectoryFullPath) {Filter = $"*{ClosedFileExtension}"};

            OnCreated(watcher, modelLifetime, projectName, portDirectoryFullPath);

            OnDeleted(lifetime, watcher, modelLifetimeDefinition);

            StartWatcher(watcher);

            myLogger.Info("RiderBackendToUnrealEditor building finished");
        }

        private static void StartWatcher(FileSystemWatcher watcher)
        {
            watcher.EnableRaisingEvents = true;
        }

        private void OnDeleted(Lifetime lifetime, FileSystemWatcher watcher,
            LifetimeDefinition modelLifetimeDefinition)
        {
            watcher.Deleted += (sender, args) =>
            {
                myLogger.Info("File with port's deleted");

                //modelLifetimeDefinition.Terminate();
                //modelLifetimeDefinition = lifetime.CreateNested();
            };
        }

        private void OnCreated(FileSystemWatcher watcher, Lifetime modelLifetime, string projectName,
            string portDirectoryFullPath)
        {
            var portFileFullPath = Path.Combine(portDirectoryFullPath, PortFileName);

            var portFileClosedPath = Path.Combine(portDirectoryFullPath, $"{PortFileName}{ClosedFileExtension}");
            Directory.CreateDirectory(portDirectoryFullPath);
            FileSystemEventHandler handler = (_, e) =>
            {
                Assertion.Assert(portFileClosedPath.Equals(e.FullPath), "Invalid event received from watcher");
                myLogger.Info("File with port's created");

                var text = File.ReadAllText(portFileFullPath);
                if (!int.TryParse(text, out var port))
                {
                    myLogger.Error("Couldn't parse port for from file:{0}, text:{1}", portFileFullPath, text);
                    return;
                }

                var wire = new SocketWire.Client(modelLifetime, myDispatcher, port, "UnrealEditorClient");
                wire.Connected.Advise(modelLifetime, isConnected => myUnrealHost.PerformModelAction(riderModel => 
                    riderModel.IsConnectedToUnrealEditor.SetValue(isConnected)));

                //todo think about alive file from previous session

                myLogger.Info("WireConnected");
                var serializers = new Serializers();
                var identities = new Identities(IdKind.Client);
                var protocol = new Protocol($"UnrealRiderClient-{projectName}", serializers, identities,
                    myDispatcher, wire, modelLifetime);
                wire.Connected.WhenTrue(modelLifetime, lf =>
                {
                    ResetModel(lf, protocol);
                });
            };
            watcher.Created += handler;
            watcher.Changed += handler;
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

            myEditorModel.SetValue(lf, new RdEditorModel(lf, protocol));
            myEditorModel.View(lf,
                (viewLifetime, unrealModel) =>
                {
                    UE4Library.RegisterDeclaredTypesSerializers(unrealModel.SerializationContext.Serializers);

                    unrealModel.AllowSetForegroundWindow.Set((lt, pid) =>
                    {
                        return myUnrealHost.PerformModelAction(riderModel =>
                            riderModel.AllowSetForegroundWindow.Start(lt, pid)) as RdTask<bool>;
                    });

                    unrealModel.UnrealLog.Advise(viewLifetime,
                        logEvent =>
                        {
                            myUnrealHost.PerformModelAction(riderModel => { OnMessageReceived(riderModel, logEvent); });
                        });

                    unrealModel.OnBlueprintAdded.Advise(viewLifetime, blueprintClass =>
                    {
                        //todo
                    });

                    unrealModel.Play.Advise(viewLifetime, val =>
                    {
                        myUnrealHost.PerformModelAction(riderModel =>
                        {
                            if (PlayedFromRider)
                                return;
                            try
                            {
                                PlayedFromUnreal = true;
                                riderModel.Play.Set(val);
                            }
                            finally
                            {
                                PlayedFromUnreal = false;
                            }
                        });
                    });
                    unrealModel.PlayMode.Advise(viewLifetime, val =>
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
                        riderModel.OpenBlueprint.Advise(viewLifetime, blueprintReference =>
                            OnOpenedBlueprint(unrealModel, blueprintReference));

                        riderModel.NavigateToClass.Advise(viewLifetime,
                            uClass => myEditorNavigator.NavigateToClass(uClass));

                        riderModel.NavigateToMethod.Advise(viewLifetime,
                            methodReference => myEditorNavigator.NavigateToMethod(methodReference));

                        riderModel.Play.Advise(viewLifetime, val =>
                        {
                            if (PlayedFromUnreal)
                                return;
                            try
                            {
                                PlayedFromRider = true;
                                unrealModel.Play.Set(val);
                            }
                            finally
                            {
                                PlayedFromRider = false;
                            }
                        });
                        
                        riderModel.PlayMode.Advise(viewLifetime, val =>
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
                        riderModel.FrameSkip.Advise(viewLifetime, skip =>
                            unrealModel.FrameSkip.Fire(skip));
                    });
                });
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