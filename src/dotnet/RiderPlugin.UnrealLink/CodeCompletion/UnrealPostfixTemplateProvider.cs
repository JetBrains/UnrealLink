using JetBrains.Annotations;
using JetBrains.Application.Settings;
using JetBrains.Diagnostics;
using JetBrains.ReSharper.Feature.Services.CodeCompletion;
using JetBrains.ReSharper.Feature.Services.CodeCompletion.Infrastructure;
using JetBrains.ReSharper.Feature.Services.CodeCompletion.Infrastructure.LookupItems;
using JetBrains.ReSharper.Feature.Services.Cpp.CodeCompletion;
using JetBrains.ReSharper.Feature.Services.PostfixTemplates.Settings;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Cpp.Language;
using JetBrains.ReSharper.Psi.Cpp.Tree;

namespace RiderPlugin.UnrealLink.CodeCompletion;

[Language(typeof(CppLanguage))]
public class UnrealPostfixTemplateProvider : ItemsProviderOfSpecificContext<CppCodeCompletionContext>
{
    protected override bool IsAvailable(CppCodeCompletionContext context)
    {
        if (!context.IsUESolution) return false;
        
        var basicContext = context.BasicContext;

        return
            basicContext.CodeCompletionType == CodeCompletionType.BasicCompletion &&
            basicContext.ContextBoundSettingsStore.GetValue(PostfixTemplatesSettingsAccessor.ShowPostfixItems) &&
            GetQualRef(context) != null;
    }

    [CanBeNull]
    private static QualifiedReference GetQualRef(CppCodeCompletionContext context)
    {
        if (context.UnterminatedContext.Reference is not ICppReference reference)
            return null;

        return reference.GetCppTreeNode() as QualifiedReference;
    }

    protected override bool AddLookupItems(CppCodeCompletionContext context, IItemsCollector collector)
    {
        var reference = GetQualRef(context);
        Assertion.AssertNotNull(reference, "CppPostfixTemplateProvider is available only on reference");
        return base.AddLookupItems(context, collector);
    }
}