using System;
using JetBrains.Annotations;
using JetBrains.Application.UI.PopupLayout;
using JetBrains.Application.UI.Tooltips;
using JetBrains.DocumentModel;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.CodeCompletion.Infrastructure;
using JetBrains.ReSharper.Feature.Services.Cpp.Caches;
using JetBrains.ReSharper.Feature.Services.Cpp.CodeStyle.IncludesOrder;
using JetBrains.ReSharper.Feature.Services.Lookup;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Cpp.Language;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Transactions;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.TextControl;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.CodeCompletion;

public abstract class UnrealPostfixLookupItem : UnrealPostfixTemplateLookupItem
{
    protected abstract string TextPrefix();
    protected abstract string TextSuffix();
    protected abstract string NecessaryHeaderName();
    protected abstract CppQualifiedId GetSearchTargetName();

    protected UnrealPostfixLookupItem([NotNull] CodeCompletionContext context,
        [NotNull] MemberAccessExpression memAccess, string name) : base(context, memAccess, name)
    {
    }

    public override void Accept(ITextControl textControl, DocumentRange nameRange,
        LookupItemInsertType lookupItemInsertType, Suffix suffix, ISolution solution, bool keepCaretStill)
    {
        var document = textControl.Document;
        var range = nameRange.TextRange;
        var headerWasNotIncluded = IncludeHeaderIfNeeded((CppFile)File.GetTheOnlyPsiFile(CppLanguage.Instance),
            document, solution, ref range);
        var file = File.GetTheOnlyPsiFile(CppLanguage.Instance);
        var sign = file?.FindNodeAt(GetSignRange(range));
        if (sign == null) return;
        
        var memAccess = (MemberAccessExpression)sign.Parent;
        var memAccessBegin = memAccess.GetDocumentStartOffset();
        if (memAccess == null) return;
        
        var memAccessQualifierEnd = memAccess.Qualifier.GetDocumentEndOffset();

        if (memAccess.PrevSibling is MacroCall prevMacroCall && memAccess.ContainsTokenFrom(prevMacroCall))
            memAccessBegin = prevMacroCall.GetDocumentStartOffset();

        var insertedText = TextPrefix();
        var typeNameEnd = memAccessQualifierEnd.Offset + insertedText.Length;
        var textSuffix = TextSuffix();

        document.DeleteText(new TextRange(memAccessQualifierEnd.Offset, range.EndOffset));
        document.InsertText(memAccessBegin.Offset, insertedText);
        document.InsertText(typeNameEnd, textSuffix);

        textControl.Caret.MoveTo(typeNameEnd + textSuffix.Length,
            CaretVisualPlacement.DontScrollIfVisible);

        if (headerWasNotIncluded)
        {
            var context = new PopupWindowContextSource(lifetime =>
#pragma warning disable CS0618
                textControl.PopupWindowContextFactory.CreatePopupWindowContext(lifetime));
#pragma warning restore CS0618
            Shell.Instance.GetComponent<ITooltipManager>()
                .Show("Header " + NecessaryHeaderName() + " included", context);
        }
    }

    private bool IncludeHeaderIfNeeded(CppFile file, IDocument document, ISolution solution, ref TextRange nameRange)
    {
        if (IsHeaderAlreadyIncluded(file))
            return false;

        var nameRangeMarker = nameRange.CreateRangeMarker(document);

        var psiServices = solution.GetPsiServices();
        var transactionUtil = psiServices.GetComponent<ICppTransactionUtil>();

        using (new PsiTransactionCookie(psiServices, DefaultAction.Commit, null))
        {
            file.InsertImportDirective(NecessaryHeaderName());
            transactionUtil.ReparsePendingChanges(File);
        }

        nameRange = nameRangeMarker.DocumentRange.TextRange;
        return true;
    }

    private bool IsHeaderAlreadyIncluded(CppFile file)
    {
        var globalNamespace = file.GetFileCache().ResolveCache.GlobalNamespace;
        return !globalNamespace.ChildByName(GetSearchTargetName(), CppLocationAnchor.BottomMost()).IsEmpty();
    }
}

public class UnrealPostfixTextLookupItem : UnrealPostfixLookupItem {
    public UnrealPostfixTextLookupItem([NotNull] CodeCompletionContext context, [NotNull] MemberAccessExpression memAccess, string name) : base(context, memAccess, name)
    {
    }

    protected override string TextPrefix() => "TEXT(";

    protected override string TextSuffix() => ")";

    protected override string NecessaryHeaderName() => "";

    protected override CppQualifiedId GetSearchTargetName() => new("TEXT");
}