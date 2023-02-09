using System;
using JetBrains.Core;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.Rd.Tasks;

namespace RiderPlugin.UnrealLink.LiveCoding;

[SolutionComponent]
public class LiveCodingInteractionProvider
{
    private readonly RiderBackendToUnrealEditor _riderToEditor;
    private readonly LifetimeDefinition _lifetimeDef;

    public LiveCodingInteractionProvider(RiderBackendToUnrealEditor RiderToEditor)
    {
        _riderToEditor = RiderToEditor;
        _lifetimeDef = RiderToEditor.NestedLifetime();
    }

    public IRdTask<bool> HasStarted() => _riderToEditor.EditorModel.LC_HasStarted.Start(_lifetimeDef.Lifetime, Unit.Instance);
    public IRdTask<bool> IsCompiling() => _riderToEditor.EditorModel.LC_IsCompiling.Start(_lifetimeDef.Lifetime, Unit.Instance);
    public IRdTask<bool> CanEnableForSession() => _riderToEditor.EditorModel.LC_CanEnableForSession.Start(_lifetimeDef.Lifetime, Unit.Instance);
    public IRdTask<bool> IsEnabledForSession() => _riderToEditor.EditorModel.LC_IsEnabledForSession.Start(_lifetimeDef.Lifetime, Unit.Instance);
    public IRdTask<bool> IsEnabledByDefault() => _riderToEditor.EditorModel.LC_IsEnabledByDefault.Start(_lifetimeDef.Lifetime, Unit.Instance);
    public void Compile() => _riderToEditor.EditorModel.LC_Compile();
    public void EnableByDefault(bool enable) => _riderToEditor.EditorModel.LC_EnableByDefault(enable);
    public void EnableForSession(bool enable) => _riderToEditor.EditorModel.LC_EnableForSession(enable);

    public void SubscribeOnPatchComplete(Action<Unit> handle) =>
        _riderToEditor.EditorModel.LC_OnPatchComplete.Advise(_lifetimeDef.Lifetime, handle);


}