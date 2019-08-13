using System;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using JetBrains.Collections.Viewable;
using JetBrains.DataFlow;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.Platform.Unreal.EditorPluginModel;
using JetBrains.ProjectModel;
using JetBrains.Rd;
using JetBrains.Rd.Impl;
using JetBrains.ReSharper.Features.XamlRendererHost.Preview;
using JetBrains.Rider.Model;
using JetBrains.Util;

namespace ReSharperPlugin.UnrealEditor
{
    [SolutionComponent]
    public class RiderBackendToUnrealEditor
    {
        private readonly IScheduler myDispatcher;
        private readonly ISolution mySolution;
        private readonly ILogger myLogger;
        private readonly UnrealHost myUnrealHost;
        private readonly IProperty<RdEditorModel> myEditorModel;

        private const string PortFileName = "UnrealProtocolPort.txt";
        private const string ClosedFileExtension = ".closed";

        public RiderBackendToUnrealEditor(Lifetime lifetime, IScheduler dispatcher, ISolution solution, ILogger logger,
            UnrealHost unrealHost)
        {
            myLogger.Info("RiderBackendToUnrealEditor building started");
            System.Diagnostics.Debugger.Launch();
            myDispatcher = dispatcher;
            mySolution = solution;
            myLogger = logger;
            myUnrealHost = unrealHost;
            var modelLifetimeDefinition = lifetime.CreateNested();
            var modelLifetime = modelLifetimeDefinition.Lifetime;
            myEditorModel = new Property<RdEditorModel>(modelLifetime, "RiderTounrealModel");

            /*string portString;
            try
            {
                portString = Environment.GetEnvironmentVariable(PortFileName);
            }
            catch (SecurityException e)
            {
                myLogger.Error($"Couldn't get environment variable:{PortFileName}", e);
                throw;
            }*/


/*
            if (portString != null)
            {
                //Editor's already opened
                if (int.TryParse(portString, out int port))
                {
                    wire = new SocketWire.Client(lifetime, myDispatcher, port, "UnrealEditorClient");
                    protocol = () => new Protocol("UnrealRiderClient", new Serializers(), new Identities(IdKind.Client),
                        myDispatcher, wire, lifetime);
                }
                else
                {
                    throw new ArgumentException($"Invalid port:{port}");
                }
            }
            else
            {
                //Editor's not opened yet
                wire = new SocketWire.Server(lifetime, myDispatcher, null, "UnrealServer");
                protocol = () => new Protocol("UnrealRiderServer", new Serializers(), new Identities(IdKind.Server),
                    myDispatcher, wire, lifetime);
                var unrealProtocolPortProperty = myUnrealHost.GetValue(model => model.Rider_backend_to_unreal_editor_port);
                wire.Connected.WhenTrue(lifetime, lf => unrealProtocolPortProperty.Value = wire.Port);
            }
*/
            var projectName = mySolution.Name;
            var portDirectoryFullPath = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "..",
                "Local", "Jetbrains", "Rider", "Unreal", projectName, "Ports");
            var portFileFullPath = Path.Combine(portDirectoryFullPath, PortFileName);

            var portFileClosedPath = Path.Combine(portDirectoryFullPath, $"{PortFileName}{ClosedFileExtension}");
            Directory.CreateDirectory(portDirectoryFullPath);

            var watcher = new FileSystemWatcher(portDirectoryFullPath) {Filter = $"*{ClosedFileExtension}"};
            watcher.Created += (_, e) =>
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

                //todo think about alive file from previous session

                wire.Connected.WhenTrue(modelLifetime, lf =>
                {
                    myLogger.Info("WireConnected");
                    var serializers = new Serializers();
                    var identities = new Identities(IdKind.Client);
                    var protocol = new Protocol($"UnrealRiderClient-{projectName}", serializers, identities,
                        myDispatcher, wire, modelLifetime);
                    myEditorModel.SetValue(lf, new RdEditorModel(lf, protocol));
                    myEditorModel.View(lf,
                        (lf2, model) =>
                        {
                            model.UnrealLog.Advise(lf, s =>
                            {
                                //parse url, blueprints, etc...

                                var linkParser = new Regex(@"\b(?:https?://|www\.)\S+\b",
                                    RegexOptions.Compiled | RegexOptions.IgnoreCase);
                                StringRange[] array = linkParser.Matches(s.Message.Data)
                                    .OfType<Match>()
                                    .Select(match => new StringRange(match.Index, match.Index + match.Length))
                                    .ToArray();

                                myUnrealHost.PerformModelAction(m =>
                                    m.UnrealLog.Fire(new RdLogMessage(s, array, array)));
                            });
                        });
                });
            };


            watcher.Deleted += (sender, args) =>
            {
                myLogger.Info("File with port's deleted");

                modelLifetimeDefinition.Terminate();
                modelLifetimeDefinition = lifetime.CreateNested();
            };

            watcher.EnableRaisingEvents = true;

            myLogger.Info("RiderBackendToUnrealEditor building finished");
        }
    }
}