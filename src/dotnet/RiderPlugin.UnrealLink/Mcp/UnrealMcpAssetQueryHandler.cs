using System;
using System.Collections.Generic;
using System.Linq;
using JetBrains.Annotations;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.ProjectModel;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Reader;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Reader.Entities.Properties;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Search;
using JetBrains.ReSharper.Psi.Cpp.Caches;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.UE4;
using JetBrains.Util;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink.Mcp;

[SolutionComponent(InstantiationEx.LegacyDefault)]
public class UnrealMcpAssetQueryHandler
{
    public UnrealMcpAssetQueryHandler([NotNull] ISolution solution, [NotNull] UnrealHost unrealHost, [NotNull] UE4AssetsCache assetsCache)
    {
        unrealHost.PerformModelAction(model =>
        {
            model.SearchUnrealAssets.SetAsync((lt, request) =>
            {
                var rdTask = new RdTask<UnrealAssetSearchResponse>();
                solution.Locks.ExecuteOrQueueReadLockEx(lt, "UnrealMcp.SearchAssets", () =>
                {
                    try { rdTask.Set(SearchAssets(solution, assetsCache, request)); }
                    catch (Exception ex) { rdTask.Set(ex); }
                });
                return rdTask;
            });

            model.GetBlueprintHierarchy.SetAsync((lt, request) =>
            {
                var rdTask = new RdTask<UnrealBlueprintHierarchyResponse>();
                solution.Locks.ExecuteOrQueueReadLockEx(lt, "UnrealMcp.GetBlueprintHierarchy", () =>
                {
                    try { rdTask.Set(GetBlueprintHierarchy(solution, assetsCache, request)); }
                    catch (Exception ex) { rdTask.Set(ex); }
                });
                return rdTask;
            });

            model.SearchGameplayTags.SetAsync((lt, request) =>
            {
                var rdTask = new RdTask<UnrealGameplayTagsResponse>();
                solution.Locks.ExecuteOrQueueReadLockEx(lt, "UnrealMcp.SearchGameplayTags", () =>
                {
                    try { rdTask.Set(SearchGameplayTags(assetsCache, request)); }
                    catch (Exception ex) { rdTask.Set(ex); }
                });
                return rdTask;
            });

            model.GetAssetProperties.SetAsync((lt, request) =>
            {
                var rdTask = new RdTask<UnrealAssetPropertiesResponse>();
                solution.Locks.ExecuteOrQueueReadLockEx(lt, "UnrealMcp.GetAssetProperties", () =>
                {
                    try { rdTask.Set(GetAssetProperties(solution, assetsCache, request)); }
                    catch (Exception ex) { rdTask.Set(ex); }
                });
                return rdTask;
            });

            model.FindDefaultOverrides.SetAsync((lt, request) =>
            {
                var rdTask = new RdTask<UnrealDefaultOverridesResponse>();
                solution.Locks.ExecuteOrQueueReadLockEx(lt, "UnrealMcp.FindDefaultOverrides", () =>
                {
                    try { rdTask.Set(FindDefaultOverrides(solution, assetsCache, request)); }
                    catch (Exception ex) { rdTask.Set(ex); }
                });
                return rdTask;
            });
        });
    }

    /// <summary>
    /// Returns every short C++ class name reachable from `rootName` via UE inheritance — the root itself
    /// plus every direct and indirect subclass. Lets us answer "derived blueprints of an abstract base"
    /// by feeding each concrete subclass name to <see cref="UE4AssetsCache.GetBaseClassesByShortName"/>.
    /// <para/>
    /// The C++ symbol cache stores UE classes with their UE prefix (`ULyraCameraMode`); the asset cache
    /// uses the prefix-stripped form (`LyraCameraMode`). Callers may supply either, so we probe both.
    /// </summary>
    [NotNull]
    private static IReadOnlyCollection<string> BuildCppClassClosure([NotNull] ISolution solution, [NotNull] string rootShortName)
    {
        var closure = new HashSet<string> { rootShortName };
        // The asset index keys are prefix-stripped, so also seed with the stripped variant
        // if the caller passed the C++ form ('ULyraCameraMode' → 'LyraCameraMode').
        var stripped = UnrealPrefixes.StripUnrealPrefix(rootShortName);
        if (stripped != null) closure.Add(stripped);

        var globalSymbolCache = solution.GetComponent<CppGlobalSymbolCache>();
        var nameCache = globalSymbolCache.SymbolNameCache;
        var linkageCache = globalSymbolCache.LinkageCache;

        foreach (var lookupName in EnumerateCppLookupNames(rootShortName))
        {
            foreach (var classSymbol in UE4Util.GetGlobalClassSymbols(lookupName, nameCache).Where(CppUE4Util.IsUEType))
            {
                var rootEntity = linkageCache.FindEntityBySymbol(classSymbol);
                if (rootEntity == null) continue;

                foreach (var entry in CppInheritanceUtil.FindAllDerivedLinkageEntities(linkageCache, rootEntity))
                {
                    if (entry.Key.Name.AsQualifiedId() is not {} qualifiedId)
                        continue;
                    // Asset index uses prefix-stripped UE form; add both so downstream lookups
                    // by either convention hit.
                    closure.Add(qualifiedId.Name);
                    var derivedStripped = UnrealPrefixes.StripUnrealPrefix(qualifiedId.Name);
                    if (derivedStripped != null) closure.Add(derivedStripped);
                }
            }
        }
        return closure;
    }

    /// <summary>
    /// Yields the original name, followed by each common UE-prefixed variant if the original was
    /// passed in stripped form (`LyraCameraMode` → also `ULyraCameraMode`, `ALyraCameraMode`, …).
    /// </summary>
    [NotNull]
    private static IEnumerable<string> EnumerateCppLookupNames([NotNull] string shortName)
    {
        yield return shortName;
        if (UnrealPrefixes.GetPrefixIfAny(shortName).Length != 0)
            yield break;
        foreach (var prefix in UnrealPrefixes.GetClassPrefixes())
            yield return prefix + shortName;
    }

    [NotNull]
    private static UnrealAssetSearchResponse SearchAssets([NotNull] ISolution solution, [NotNull] UE4AssetsCache cache, [NotNull] UnrealAssetSearchRequest request)
    {
        var limit = Math.Max(1, Math.Min(request.Limit, 5000));
        var results = new List<UnrealAssetInfo>();

        bool MatchesPath(string fullPath) =>
            request.PackagePath == null || DiskPathMatchesPackagePrefix(fullPath, request.PackagePath);

        if (request.BaseClass != null)
        {
            var query = request.Query;
            foreach (var shortName in BuildCppClassClosure(solution, request.BaseClass))
            {
                foreach (var baseFqn in cache.GetBaseClassesByShortName(shortName))
                {
                    foreach (var cls in UE4SearchUtil.GetDerivedBlueprintClasses(baseFqn, cache))
                    {
                        if (results.Count >= limit) goto done;
                        if (!cls.ContainingFile.IsValid()) continue;
                        if (query != null && !cls.Name.Contains(query, StringComparison.OrdinalIgnoreCase))
                            continue;
                        var path = cls.ContainingFile.GetLocation().FullPath;
                        if (!MatchesPath(path)) continue;
                        results.Add(new UnrealAssetInfo(path, cls.Name, request.BaseClass));
                    }
                }
            }
            done: ;
        }
        else if (request.Query != null)
        {
            foreach (var file in cache.GetAssetFilesContainingWord(request.Query))
            {
                if (results.Count >= limit) break;
                var path = file.GetLocation().FullPath;
                if (!MatchesPath(path)) continue;
                results.Add(new UnrealAssetInfo(path, file.GetLocation().NameWithoutExtension, null));
            }
        }

        return new UnrealAssetSearchResponse(results);
    }

    /// <summary>
    /// Match a disk asset path against an Unreal package-path prefix
    /// (e.g. "/Game/Heroes/" or "/MyPlugin/Content/").
    /// <para/>
    /// `/Game/X/Y` resolves on disk under `<ProjectDir>/Content/X/Y.uasset`;
    /// `/PluginName/X/Y` resolves under `…/Plugins/<PluginName>/Content/X/Y.uasset`.
    /// We invert the convention here: strip the disk path back to its mount-root form
    /// and compare prefixes (case-insensitive).
    /// </summary>
    private static bool DiskPathMatchesPackagePrefix([NotNull] string fullPath, [NotNull] string packagePathPrefix)
    {
        if (packagePathPrefix.Length == 0) return true;
        var normalised = fullPath.Replace('\\', '/');
        var contentIdx = normalised.LastIndexOf("/Content/", StringComparison.OrdinalIgnoreCase);
        if (contentIdx < 0) return false;

        var afterContent = normalised.Substring(contentIdx + "/Content".Length);
        // Strip extension.
        var dot = afterContent.LastIndexOf('.');
        if (dot > 0) afterContent = afterContent.Substring(0, dot);

        // Determine the mount root.
        var beforeContent = normalised.Substring(0, contentIdx);
        var pluginsIdx = beforeContent.LastIndexOf("/Plugins/", StringComparison.OrdinalIgnoreCase);
        string mountRoot;
        if (pluginsIdx >= 0)
        {
            var afterPlugins = beforeContent.Substring(pluginsIdx + "/Plugins/".Length);
            var slash = afterPlugins.IndexOf('/');
            mountRoot = "/" + (slash > 0 ? afterPlugins.Substring(0, slash) : afterPlugins);
        }
        else
        {
            mountRoot = "/Game";
        }

        var reconstructed = mountRoot + afterContent; // e.g. /Game/Heroes/BP_Hero
        var prefix = packagePathPrefix.TrimEnd('/');
        return reconstructed.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)
            && (reconstructed.Length == prefix.Length || reconstructed[prefix.Length] == '/');
    }

    [NotNull]
    private static UnrealBlueprintHierarchyResponse GetBlueprintHierarchy([NotNull] ISolution solution, [NotNull] UE4AssetsCache cache, [NotNull] UnrealBlueprintHierarchyRequest request)
    {
        var limit = Math.Max(1, Math.Min(request.Limit, 5000));
        var seen = new HashSet<string>();
        var blueprints = new List<UnrealBlueprintInfo>();
        foreach (var shortName in BuildCppClassClosure(solution, request.BaseClass))
        {
            foreach (var baseFqn in cache.GetBaseClassesByShortName(shortName))
            {
                foreach (var cls in UE4SearchUtil.GetDerivedBlueprintClasses(baseFqn, cache))
                {
                    if (blueprints.Count >= limit) goto done;
                    if (!cls.ContainingFile.IsValid()) continue;
                    if (!seen.Add(cls.Name)) continue;
                    blueprints.Add(new UnrealBlueprintInfo(cls.Name, cls.ContainingFile.GetLocation().FullPath));
                }
            }
        }
        done:
        return new UnrealBlueprintHierarchyResponse(blueprints);
    }

    [NotNull]
    private static UnrealDefaultOverridesResponse FindDefaultOverrides([NotNull] ISolution solution, [NotNull] UE4AssetsCache cache, [NotNull] UnrealDefaultOverridesRequest request)
    {
        var limit = Math.Max(1, Math.Min(request.Limit, 5000));
        var results = new List<UnrealDefaultOverrideInfo>();
        var seenBlueprints = new HashSet<string>();

        // Resolve the full C++ inheritance closure once — `request.ClassName` may name an abstract base
        // (e.g. ULyraCameraMode) whose CDO overrides live only on Blueprints of its concrete subclasses.
        var closure = BuildCppClassClosure(solution, request.ClassName);

        foreach (var shortName in closure)
        {
            foreach (var baseFqn in cache.GetBaseClassesByShortName(shortName))
            {
                foreach (var bpClass in UE4SearchUtil.GetDerivedBlueprintClasses(baseFqn, cache))
                {
                    if (results.Count >= limit) goto done;
                    if (!bpClass.ContainingFile.IsValid()) continue;
                    if (!seenBlueprints.Add(bpClass.Name)) continue;

                    var accessor = cache.GetUEAssetFileAccessor(bpClass.ContainingFile);
                    if (!accessor.TryGetValue(linker => linker.ExportMap.FirstOrDefault(e => e.IsClassDefaultObject),
                            out var cdoExport) || cdoExport == null)
                        continue;

                    var prop = cdoExport.ReadProperties().FirstOrDefault(p => p.Name == request.FieldName);
                    if (prop?.ValuePresentation == null) continue;

                    results.Add(new UnrealDefaultOverrideInfo(
                        bpClass.ContainingFile.GetLocation().FullPath,
                        cdoExport.ObjectStringName,
                        prop.TypeName,
                        prop.ValuePresentation));
                }
            }
        }
        done:

        return new UnrealDefaultOverridesResponse(results);
    }

    [NotNull]
    private static UnrealGameplayTagsResponse SearchGameplayTags([NotNull] UE4AssetsCache cache, [NotNull] UnrealGameplayTagsRequest request)
    {
        var tagLocations = request.Prefix != null
            ? cache.FindGameplayTagsByPrefix(request.Prefix)
            : cache.FindGameplayTagsByPrefix("");
        var tags = tagLocations
            .Where(t => t.File.IsValid())
            .Take(request.Limit)
            .Select(t => new UnrealGameplayTagInfo(t.TagName, t.File.GetLocation().FullPath))
            .ToList();
        return new UnrealGameplayTagsResponse(tags);
    }

    [NotNull]
    private static UnrealAssetPropertiesResponse GetAssetProperties([NotNull] ISolution solution, [NotNull] UE4AssetsCache cache, [NotNull] UnrealAssetPropertiesRequest request)
    {
        var path = VirtualFileSystemPath.Parse(request.AssetPath, solution.GetInteractionContext());
        IPsiSourceFile sourceFile = null;
        // UE assets are tracked via UE4AssetAdditionalFilesModuleFactory as misc project items,
        // so the IPsiSourceFile lives on the additional-files module — enumerate every PSI source
        // file the platform attaches to the matched IProjectFile and pick the UnrealAssetFileType one.
        var psiModules = solution.GetPsiServices().Modules;
        foreach (var projectFile in solution.FindProjectItemsByLocation(path).OfType<IProjectFile>())
        {
            foreach (var candidate in psiModules.GetPsiSourceFilesFor(projectFile))
            {
                if (candidate != null && candidate.IsValid() && candidate.LanguageType.Is<UnrealAssetFileType>())
                {
                    sourceFile = candidate;
                    break;
                }
            }
            if (sourceFile != null) break;
        }

        if (sourceFile == null)
            return new UnrealAssetPropertiesResponse(null, new List<UnrealAssetPropertyInfo>());

        var accessor = cache.GetUEAssetFileAccessor(sourceFile);
        string objectName = null;
        var propertyInfos = new List<UnrealAssetPropertyInfo>();

        accessor.TryGetValue(linker =>
        {
            var export = linker.ExportMap.FirstOrDefault(e => e.IsClassDefaultObject)
                         ?? linker.ExportMap.FirstOrDefault(e => !e.IsBlueprintGeneratedClass() && !e.IsFunction());
            if (export == null) return false;
            objectName = export.ObjectStringName;
            propertyInfos = export.ReadProperties()
                .Select(p => new UnrealAssetPropertyInfo(p.Name, p.TypeName, p.ValuePresentation ?? ""))
                .ToList();
            return true;
        }, out _);

        return new UnrealAssetPropertiesResponse(objectName, propertyInfos);
    }

}
