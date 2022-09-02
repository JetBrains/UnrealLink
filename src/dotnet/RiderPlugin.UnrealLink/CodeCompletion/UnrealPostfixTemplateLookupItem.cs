using JetBrains.Annotations;
using JetBrains.DocumentModel;
using JetBrains.ReSharper.Feature.Services.CodeCompletion.Infrastructure;
using JetBrains.ReSharper.Feature.Services.CodeCompletion.Infrastructure.LookupItems;
using JetBrains.ReSharper.Feature.Services.CodeCompletion.Infrastructure.LookupItems.Impl;
using JetBrains.ReSharper.Feature.Services.CodeCompletion.Infrastructure.Match;
using JetBrains.ReSharper.Feature.Services.Cpp.Options;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.UI.Icons;
using JetBrains.UI.RichText;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.CodeCompletion;

public class UnrealPostfixTemplateLookupItem : TextLookupItemBase
{
    public override IconId Image => CppThemedIcons.Unreal.Id;
    
    protected readonly IPsiSourceFile File;
    protected readonly string myName;

    private readonly int mySignRelativeStartOffset;
    private readonly DocumentRange mySignDocumentRange;

    private const int ExpressionBit = 8;

    protected UnrealPostfixTemplateLookupItem(
        [NotNull] CodeCompletionContext context, [NotNull] MemberAccessExpression memAccess, string name)
    {
        File = context.SourceFile;
        myName = name;
        Text = name;
        mySignDocumentRange = memAccess.Sign.GetDocumentRange();
        mySignRelativeStartOffset = mySignDocumentRange.StartOffset.Offset - memAccess.Member.GetDocumentStartOffset().Offset;

        Placement = new LookupItemPlacement(name);
        Placement.Relevance |= 1UL << ExpressionBit;
    }

    [Pure]
    protected DocumentRange GetSignRange(TextRange nameRange)
    {
        return new DocumentRange(mySignDocumentRange.Document, TextRange.FromLength(nameRange.StartOffset + mySignRelativeStartOffset, mySignDocumentRange.Length));
    }

    protected static RichText CreateDisplayName(string s)
    {
        return new RichText(s, new TextStyle(JetFontStyles.Bold));
    }

    public override MatchingResult Match(PrefixMatcher prefixMatcher)
    {
        return prefixMatcher.Match(myName);
    }
}