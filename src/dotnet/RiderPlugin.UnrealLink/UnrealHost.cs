using System;
using JetBrains.Application.Threading;
using JetBrains.DataFlow;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.Rd.Base;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.ReSharper.Psi.Cpp.UE4;
using RiderPlugin.UnrealLink.Model;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent]
    public class UnrealHost
    {
        private readonly Lifetime myLifetime;

        // TO-DO: frontend isn't up in backend tests
        private readonly bool myIsInTests;

        public readonly RdRiderModel myModel;

        public UnrealHost(Lifetime lifetime, ISolution solution, IShellLocks locks, ICppUE4SolutionDetector solutionDetector)
        {
            myIsInTests = locks.Dispatcher.IsAsyncBehaviorProhibited;
            if (myIsInTests)
                return;

            myLifetime = lifetime;   
            myModel = solution.GetProtocolSolution().GetRdRiderModel();
            solutionDetector.IsUnrealSolution.Change.Advise_HasNew(myLifetime, args =>
            {
                myModel.IsUnrealEngineSolution.Set(args.New);
                myModel.IsUproject.Set(args.New && solutionDetector.SupportRiderProjectModel == CppUE4ProjectModelSupportMode.UprojectOpened);
                myModel.IsPreBuiltEngine.Set(args.New && !solutionDetector.UnrealContext.Value.IsBuiltFromSource);
            });
            
            if (myModel.TryGetProto() is {} protocol)
            {
                UE4Library.RegisterDeclaredTypesSerializers(protocol.Serializers);
            }
        }

        public void PerformModelAction(Action<RdRiderModel> action)
        {
            if (myIsInTests)
                return;

            action(myModel);
        }
        
        public T PerformModelAction<T>(Func<RdRiderModel, T> action)
        {
            
            if (myIsInTests)
                return default;

            return action(myModel);
        }

        public T GetValue<T>(Func<RdRiderModel, T> getter)
        {
            return getter(myModel);
        }
    }
}