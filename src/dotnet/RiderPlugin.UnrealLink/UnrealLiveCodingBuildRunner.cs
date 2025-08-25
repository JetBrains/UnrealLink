using System.Collections.Generic;
using System.Threading.Tasks;
using JetBrains.Application.Parts;
using JetBrains.Collections.Viewable;
using JetBrains.DataFlow;
using JetBrains.Lifetimes;
using JetBrains.Platform.BuildEvents;
using JetBrains.Platform.MsBuildHost.Models;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Features.SolutionBuilders;
using JetBrains.ProjectModel.Tasks.Listeners;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.ReSharperCpp.RiderPlugin.Build;
using RiderPlugin.UnrealLink.Model;

namespace RiderPlugin.UnrealLink;

[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
public class UnrealLiveCodingBuildRunner(
    Lifetime lifetime,
    ISolution solution,
    CppUE4SolutionDetector solutionDetector,
    CppUE4UbtBuildRunner buildRunner)
    : ISolutionBuilderRunner, ISolutionLoadTasksSolutionStructureReadyListener2
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
        var parser = new CppUE4UbtBuildOutputParser();
        var saver = new InFileBuildSessionSaver(def.Lifetime, request.EventsStoragePath,
            InFileBuildSessionSaver.EnabledFeatures.Events | InFileBuildSessionSaver.EnabledFeatures.Projects);

        editorModel.IsHotReloadCompiling.Change.AdviseUntil(def.Lifetime, val =>
        {
            if (!val)
            {
                // Wait for last live coding messages
                def.Lifetime.StartBackgroundAsync(async () =>
                {
                    await Task.Delay(100, def.Lifetime);
                    await def.Lifetime.StartMainRead(() =>
                    {
                        request.State.Value = BuildRunState.Completed;
                    });
                });
                return true;
            }

            return false;
        });
        editorModel.UnrealLog.Advise(def.Lifetime, logEvent =>
        {
            var category = logEvent.Info.Category.Data;
            if (category == "LogLiveCoding")
            {
                
                var outputKind = logEvent.Info.Type switch
                {
                    VerbosityType.Error => OutputKind.Error,
                    VerbosityType.Warning => OutputKind.Warning,
                    _ => OutputKind.Message
                };
                var message = logEvent.Text.Data;
                switch (outputKind)
                {
                    case OutputKind.Error:
                        request.AddError(message);
                        break;
                    case OutputKind.Warning:
                        request.AddWarning(message);
                        break;
                }
                
                request.AddOutputBuildMessage(outputKind, message);
                return;
            }
            
            parser.ConsumeUnrealBuildToolMessage(request, null, new RdProjectId(-1), logEvent.Text.Data, saver);
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

    IEnumerable<SolutionLoadTasksListenerExecutionStep> ISolutionLoadTasksSolutionStructureReadyListener2.OnSolutionLoadSolutionStructureReady()
    {
        yield return SolutionLoadTasksListenerExecutionStep.YieldToMainThreadGuarded;
        solutionDetector.IsUnrealSolution.FlowInto(lifetime, IsAvailable);
    }
}