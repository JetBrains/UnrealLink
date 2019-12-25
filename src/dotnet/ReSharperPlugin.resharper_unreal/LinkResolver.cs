using System;
using System.Collections.Generic;
using System.Linq;
using JetBrains.Annotations;
using JetBrains.ProjectModel;
using JetBrains.Rd.Tasks;
using JetBrains.Rider.Model;
using JetBrains.Unreal.Lib;
using JetBrains.Util.DataStructures;
using JetBrains.Util;

namespace ReSharperPlugin.UnrealEditor
{
    [SolutionComponent]
    public class UnrealLinkResolver
    {
        private readonly ISolution _solution;
        private readonly UnrealHost _unrealHost;
        private readonly ILogger _logger;
        private static readonly CompactMap<char, char> PairSymbol = new CompactMap<char, char>();

        static UnrealLinkResolver()
        {
            PairSymbol.Add(')', '(');
            PairSymbol.Add(']', '[');
            PairSymbol.Add('\'', '\'');
            PairSymbol.Add('"', '"');
        }

        public UnrealLinkResolver(ISolution solution, UnrealHost unrealHost, ILogger logger)
        {
            _solution = solution;
            _unrealHost = unrealHost;
            _logger = logger;
        }

        [CanBeNull]
        private FileSystemPath ConvertToAbsolutePath(FileSystemPath path)
        {
            if (path.IsAbsolute)
            {
                return path;
            }

            var possiblePaths = new List<FileSystemPath>
            {
                _solution.SolutionDirectory / "Content",
                _solution.SolutionDirectory / "Plugins",
            };

            //todo replace with UE4AssetAdditionalFilesModuleFactory

            return possiblePaths
                .SelectNotNull(possibleDir =>
                {
                    var relativePath = path.AsRelative();
                    if (relativePath == null || relativePath.IsEmpty)
                    {
                        return null;
                    }

                    var candidate = possibleDir / relativePath;
                    return candidate.Exists == FileSystemPath.Existence.Missing ? null : candidate;
                })
                .FirstOrDefault(null as FileSystemPath);
        }

        [CanBeNull]
        private ILinkResponse TryParseFullPath([NotNull] string input, [NotNull] StringRange range)
        {
            try
            {
                var path = ConvertToAbsolutePath(FileSystemPath.Parse(input));
                if (path == null)
                {
                    return null;
                }

                if (path.ExtensionNoDot == "umap")
                {
                    //todo
                    return null;
                }

                if (path.ExtensionNoDot == "uasset")
                {
                    return new LinkResponseBlueprint(new FString(path.ToUri().AbsolutePath), range);
                }

                return new LinkResponseFilePath(new FString(path.ToUri().AbsolutePath), range);
            }
            catch (InvalidPathException e)
            {
                _logger.Warn(e);
            }
            catch (Exception e)
            {
                _logger.Error(e, "occured while trying parse full path");
            }

            return null;
        }

        [CanBeNull]
        private ILinkResponse TryParseFullName([NotNull] string s, [NotNull] StringRange range,
            IRdCall<FString, bool> isBlueprintPathName)
        {
            var path = new FString(s);
            return isBlueprintPathName.Sync(path)
                ? new LinkResponseBlueprint(path, range)
                : null;
        }

        [NotNull]
        internal ILinkResponse ResolveLink(LinkRequest @struct, IRdCall<FString, bool> isBlueprintPathName)
        {
            // ReSharper disable once LocalFunctionCanBeMadeStatic
            string SqueezeBorders(string s, out int l, out int r)
            {
                l = 0;
                r = s.Length;
                if (s.EndsWith("."))
                {
                    --r;
                }

                if (PairSymbol.TryGetValue(s[r - 1], out var value))
                {
                    l = s.AsSpan(l, r - l - 1).LastIndexOf(value) + 1;
                    --r;
                }

                return s.Substring(l, r - l);
            }

            var link = @struct.Data.Data;
            var squeezed = SqueezeBorders(link, out var left, out var right);
            var range = new StringRange(left, right);

            var fullPath = TryParseFullPath(squeezed, range);
            if (fullPath != null)
            {
                return fullPath;
            }

            var fullName = TryParseFullName(squeezed, range, isBlueprintPathName);
            if (fullName != null)
            {
                return fullName;
            }


            return new LinkResponseUnresolved();
        }
    }
}