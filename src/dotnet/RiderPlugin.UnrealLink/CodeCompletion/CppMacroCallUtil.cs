using JetBrains.ReSharper.Psi.Cpp.Parsing;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Cpp.Tree.Util;
using JetBrains.ReSharper.Psi.Tree;

namespace RiderPlugin.UnrealLink.CodeCompletion;

internal static class CppMacroCallUtil
{
    public static bool ContainsTokenFrom(this ITreeNode node, MacroCall prevMacroCall)
    {
        foreach (var token in CppTreeNodeExtensions.Tokens(node))
        {
            if (token is CppFromSubstitutionTokenNode tokenFromSubstitution &&
                ReferenceEquals(tokenFromSubstitution.FindPrototypeMacro(), prevMacroCall))
            {
                return true;
            }
        }

        return false;
    }
}