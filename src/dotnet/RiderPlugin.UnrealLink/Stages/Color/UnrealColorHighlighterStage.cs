using JetBrains.Application.Settings;
using JetBrains.ReSharper.Feature.Services.Cpp.Daemon;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Psi.Cpp.Tree;

namespace RiderPlugin.UnrealLink.Stages.Color;

[DaemonStage(StagesBefore = new[] { typeof(CppIdentifierHighlightingStage) })]
public class UnrealColorHighlighterStage : CppDaemonStageBase
{
    public UnrealColorHighlighterStage(ElementProblemAnalyzerRegistrar elementProblemAnalyzerRegistrar) : base(elementProblemAnalyzerRegistrar)
    {
    }

    protected override IDaemonStageProcess CreateProcess(IDaemonProcess process, IContextBoundSettingsStore settings,
        DaemonProcessKind processKind, CppFile file)
    {
        throw new System.NotImplementedException();
    }

    protected override bool ShouldWorkInNonUserFile()
    {
        throw new System.NotImplementedException();
    }
}