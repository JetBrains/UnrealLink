using System;
using System.IO;
using JetBrains.Collections.Viewable;
using JetBrains.DataFlow;
using JetBrains.Lifetimes;
using JetBrains.Platform.Unreal.EditorPluginModel;
using JetBrains.ProjectModel;
using JetBrains.Rd;
using JetBrains.Rd.Base;
using JetBrains.Rd.Impl;
using JetBrains.ReSharper.Features.XamlRendererHost.Preview;
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
        public readonly IProperty<RdEditorModel> myEditorModel;

        public RiderBackendToUnrealEditor(Lifetime lifetime, IScheduler dispatcher, ISolution solution, ILogger logger, UnrealHost unrealHost)
        {
            myDispatcher = dispatcher;
            mySolution = solution;
            myLogger = logger;
            myUnrealHost = unrealHost;
            myEditorModel = new Property<RdEditorModel>(lifetime, "RiderTounrealModel");

            var solutionFolder = mySolution.SolutionFilePath.Directory;
            var appDataFolder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "..", "Local", "RiderLink.txt");
            int port = 0;
            using (TextReader reader = File.OpenText(appDataFolder))
            {
                port = int.Parse(reader.ReadLine());
            }
            
            var wire = new SocketWire.Server(lifetime, myDispatcher, null, "UnrealServer");
            using (System.IO.StreamWriter file = 
                new System.IO.StreamWriter(appDataFolder))
            {
                file.WriteLine(wire.Port);
            }
            wire.Connected.WhenTrue(lifetime, lf =>
            {
                myLogger.Info("WireConnected");
                var protocol = new Protocol("UnrealEditorPlugin", new Serializers(), new Identities(IdKind.Client), myDispatcher, wire, lf);
                myEditorModel.SetValue(lf, new RdEditorModel(lf, protocol));
                myEditorModel.View(lf, (lf2, model) =>
                {
                    model.Unreal_log.Advise(lf, s => myUnrealHost.PerformModelAction(m => m.Unreal_log.Set(s)));
                });
            });
            
        }
        
    }
}