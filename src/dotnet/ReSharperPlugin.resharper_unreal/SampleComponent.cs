using JetBrains.Application;
using JetBrains.ReSharper.Feature.Services.QuickFixes;

namespace ReSharperPlugin.UnrealEditor
{
    [ShellComponent]
    internal class SampleQuickFixRegistrarComponent
    {
        public SampleQuickFixRegistrarComponent(IQuickFixes table)
        {
            table.RegisterQuickFix<SampleHighlighting>(default, h => new SampleFix(h.Declaration), typeof(SampleFix));
        }
    }
}