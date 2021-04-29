using System;
using JetBrains.Application.Threading;
using JetBrains.DataFlow;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.Rd.Base;
using JetBrains.RdBackend.Common.Features;
using JetBrains.ReSharper.Feature.Services.Cpp.Util;
using JetBrains.ReSharper.Psi.Cpp;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent]
    public class UnrealHost
    {
        private readonly Lifetime myLifetime;

        // TODO: frontend isn't up in backend tests
        private readonly bool myIsInTests;

        public readonly RdRiderModel myModel;

        // ReSharper disable once SuggestBaseTypeForParameter
        public UnrealHost(Lifetime lifetime, ISolution solution, IShellLocks locks, CppUE4SolutionDetector solutionDetector)
        {
            myIsInTests = locks.Dispatcher.IsAsyncBehaviorProhibited;
            if (myIsInTests)
                return;

            myLifetime = lifetime;   
            myModel = solution.GetProtocolSolution().GetRdRiderModel();
            solutionDetector.IsUE4Solution_Observable.Change.Advise_HasNew(myLifetime, args =>
            {
                    myModel.IsUnrealEngineSolution.Set(args.New == TriBool.True);
            });
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