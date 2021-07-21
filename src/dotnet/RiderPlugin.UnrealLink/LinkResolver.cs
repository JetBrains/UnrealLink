using System;
using System.Collections.Generic;
using JetBrains.Annotations;
using JetBrains.Diagnostics;
using JetBrains.ProjectModel;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Psi.Cpp.UE4;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Util.DataStructures;
using JetBrains.Util;
using RiderPlugin.UnrealLink.Model;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent]
    public class UnrealLinkResolver
    {
        private readonly ISolution _solution;
        private readonly ILogger _logger;
        private readonly ICppUE4SolutionDetector _unrealEngineSolutionDetector;
        private static readonly CompactMap<char, char> PairSymbol = new CompactMap<char, char>();
        private readonly Lazy<VirtualFileSystemPath> _ue4SourcesPath;
        private readonly Lazy<List<VirtualFileSystemPath>> _possiblePaths;

        static UnrealLinkResolver()
        {
            PairSymbol.Add(')', '(');
            PairSymbol.Add(']', '[');
            PairSymbol.Add('\'', '\'');
            PairSymbol.Add('"', '"');
        }

        public UnrealLinkResolver(ISolution solution, ILogger logger,
            ICppUE4SolutionDetector unrealEngineSolutionDetector)
        {
            _solution = solution;
            _logger = logger;
            _unrealEngineSolutionDetector = unrealEngineSolutionDetector;
            var solutionDirectory = _solution.SolutionDirectory;
            _ue4SourcesPath = new Lazy<VirtualFileSystemPath>(() =>
            {
                using (ReadLockCookie.Create())
                {
                    return unrealEngineSolutionDetector.UE4SourcesPath;
                }
            });

            _possiblePaths = new Lazy<List<VirtualFileSystemPath>>(() =>
                new List<VirtualFileSystemPath>
                {
                    _ue4SourcesPath.Value,
                    _ue4SourcesPath.Value.Parent,
                    _ue4SourcesPath.Value / "Content",
                    _ue4SourcesPath.Value / "Content" / "Editor",
                    _ue4SourcesPath.Value / "Content" / "Editor" / "Slate", // FSlateStyleSet::ContentRootDir
                    _ue4SourcesPath.Value / "Plugins",

                    solutionDirectory,
                    solutionDirectory / "Content",
                    solutionDirectory / "Plugins"
                });
        }

        [CanBeNull]
        private VirtualFileSystemPath ConvertToAbsolutePath(VirtualFileSystemPath path)
        {
            if (path.IsAbsolute)
            {
                return path;
            }

            return _possiblePaths
                .Value.SelectNotNull(possibleDir =>
                {
                    var relativePath = path.AsRelative();
                    if (relativePath == null || relativePath.IsEmpty)
                    {
                        return null;
                    }

                    var candidate = possibleDir / relativePath;
                    return candidate.Exists == FileSystemPath.Existence.Missing ? null : candidate;
                })
                .FirstOrDefault(null);
        }

        [CanBeNull]
        private ILinkResponse TryParseFullPath([NotNull] string input, [NotNull] StringRange range)
        {
            try
            {
                var path = ConvertToAbsolutePath(VirtualFileSystemPath.Parse(input, InteractionContext.SolutionContext));
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
            try
            {
                return isBlueprintPathName.Sync(path)
                    ? new LinkResponseBlueprint(path, range)
                    : null;
            }
            catch (Exception e)
            {
                _logger.Warn(e, "[UnrealLink]: Failed to parse Blueprint string");
                return null;
            }
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