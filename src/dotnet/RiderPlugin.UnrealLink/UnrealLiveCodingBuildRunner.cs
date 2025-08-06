using System.Threading.Tasks;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading.Tasks;
using JetBrains.Collections.Viewable;
using JetBrains.DataFlow;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Features.SolutionBuilders;
using JetBrains.ProjectModel.Tasks;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.ReSharperCpp.RiderPlugin.Build;

namespace RiderPlugin.UnrealLink;

[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
public class UnrealLiveCodingBuildRunner(
    Lifetime lifetime,
    ISolution solution,
    CppUE4SolutionDetector solutionDetector,
    CppUE4UbtBuildRunner buildRunner)
    : ISolutionBuilderRunner, ISolutionLoadTasksSolutionStructureReadyListener
{
    public IProperty<bool> IsAvailable { get; } = new Property<bool>("IsAvailable", false);

    public bool IsDefault()
    {
        return true;
    }

    public double Priority => 120;
    public bool IsIncremental => false;
    public IProperty<bool> IsReady { get; } = new Property<bool>("IsReady", true);

    public void ExecuteBuildRequest(SolutionBuilderRequest request)
    {
        var backendToUnrealEditor = solution.GetComponent<RiderBackendToUnrealEditor>();
        var editorModel = backendToUnrealEditor.EditorModel;
        if (!request.BuildWholeSolution || request.BuildSessionTarget != BuildTarget.Instance ||
            editorModel == null || !editorModel.IsHotReloadAvailable.HasTrueValue())
        {
            buildRunner.ExecuteBuildRequest(request);
            return;
        }
        
        var def = Lifetime.Define(request.Lifetime);
        request.State.Value = BuildRunState.Running;

        editorModel.IsHotReloadCompiling.Change.AdviseUntil(def.Lifetime, val =>
        {
            if (!val)
            {
                request.State.Value = BuildRunState.Completed;
                return true;
            }

            return false;
        });
        editorModel.TriggerHotReload();

        request.ContinueWith(def.Lifetime, _ =>
        {
            def.Terminate();
        });
    }

    public void Abort(SolutionBuilderRequest request)
    {
    }

    public bool CanExecuteCustomTarget => false;
    public int GetSkippedProjectsCount(SolutionBuilderRequest request)
    {
        return 0;
    }

    public async Task OnSolutionLoadSolutionStructureReadyAsync(OuterLifetime loadLifetime, ISolutionLoadTasksSchedulerThreading threading)
    {
        await threading.YieldToIfNeeded(loadLifetime, Scheduling.MainGuard);
        solutionDetector.IsUnrealSolution.FlowInto(lifetime, IsAvailable);
    }
}