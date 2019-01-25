using System;
using JetBrains.Application.Threading;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Host.Features;
using JetBrains.Rider.Model;

namespace ReSharperPlugin.UnrealEditor
{
    [SolutionComponent]
    public class UnrealHost
    {
        // TODO: frontend isn't up in backend tests
        private readonly bool myIsInTests;

        private readonly RdRiderModel myModel;

        // ReSharper disable once SuggestBaseTypeForParameter
        public UnrealHost(ISolution solution, IShellLocks locks)
        {
            myIsInTests = locks.Dispatcher.IsAsyncBehaviorProhibited;
            if (myIsInTests)
                return;

            myModel = solution.GetProtocolSolution().GetRdRiderModel();
        }

        public void PerformModelAction(Action<RdRiderModel> action)
        {
            if (myIsInTests)
                return;

            action(myModel);
        }

        public T GetValue<T>(Func<RdRiderModel, T> getter)
        {
            return getter(myModel);
        }
    }
}