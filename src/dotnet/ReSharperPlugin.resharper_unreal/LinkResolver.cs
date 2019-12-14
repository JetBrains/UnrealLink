using System;
using System.IO;
using JetBrains.ProjectModel;
using JetBrains.Rider.Model;
using JetBrains.Unreal.Lib;
using JetBrains.Util.DataStructures;

namespace ReSharperPlugin.UnrealEditor
{
    [SolutionComponent]
    public class UnrealLinkResolver
    {
        private static readonly CompactMap<char, char> PairSymbol = new CompactMap<char, char>();

        static UnrealLinkResolver()
        {
            PairSymbol.Add(')', '(');
            PairSymbol.Add(']', '[');
            PairSymbol.Add('\'', '\'');
             PairSymbol.Add('"', '"');
        }

        internal ILinkResponse ResolveLink(LinkRequest @struct)
        {
            // ReSharper disable once LocalFunctionCanBeMadeStatic
            void SqueezeBorders(string s, ref int l, ref int r)
            {
                if (PairSymbol.TryGetValue(s[r], out var value))
                {
                    --r;
                    l = s.AsSpan(l, r).LastIndexOf(value);
                }
            }

            var link = @struct.Data.Data;
            var left = 0;
            var right = link.Length - 1;
            SqueezeBorders(link, ref left, ref right);
            var squeezed = link.Substring(left, right - left + 1);
            var range = new StringRange(left, right);
            try
            {
                if (Path.IsPathRooted(squeezed))
                {
                    return new LinkResponseFilePath(new FString(squeezed), range);
                }
            }
            catch (ArgumentException)
            {
            }

            return new LinkResponseBlueprint(new FString(squeezed), range);
            //todo
        }
    }
}