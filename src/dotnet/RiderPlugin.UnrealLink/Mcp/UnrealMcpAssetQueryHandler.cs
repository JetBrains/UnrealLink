using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using JetBrains.Annotations;
using JetBrains.Application.Parts;
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
using JetBrains.ReSharper.Resources.Shell;
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
                try { return RdTask.Successful(SearchAssets(solution, assetsCache, request)); }
                catch (Exception ex) { return RdTask.Faulted<UnrealAssetSearchResponse>(ex); }
            });

            model.GetBlueprintHierarchy.SetAsync((lt, request) =>
            {
                try { return RdTask.Successful(GetBlueprintHierarchy(solution, assetsCache, request)); }
                catch (Exception ex) { return RdTask.Faulted<UnrealBlueprintHierarchyResponse>(ex); }
            });

            model.SearchGameplayTags.SetAsync((lt, request) =>
            {
                try { return RdTask.Successful(SearchGameplayTags(assetsCache, request)); }
                catch (Exception ex) { return RdTask.Faulted<UnrealGameplayTagsResponse>(ex); }
            });

            model.GetAssetProperties.SetAsync((lt, request) =>
            {
                var rdTask = new RdTask<UnrealAssetPropertiesResponse>();
                Task.Run(() =>
                {
                    try { rdTask.Set(GetAssetProperties(solution, assetsCache, request)); }
                    catch (Exception ex) { rdTask.Set(ex); }
                });
                return rdTask;
            });

            model.FindDefaultOverrides.SetAsync((lt, request) =>
            {
                var rdTask = new RdTask<UnrealDefaultOverridesResponse>();
                Task.Run(() =>
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

        using (ReadLockCookie.Create())
        {
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
                        results.Add(new UnrealAssetInfo(cls.ContainingFile.GetLocation().FullPath, cls.Name, request.BaseClass));
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
                results.Add(new UnrealAssetInfo(file.GetLocation().FullPath, file.GetLocation().NameWithoutExtension, null));
            }
        }

        return new UnrealAssetSearchResponse(results);
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

        using (ReadLockCookie.Create())
        {
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
            done: ;
        }

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
        using (ReadLockCookie.Create())
        {
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
        }

        if (sourceFile == null)
            return new UnrealAssetPropertiesResponse(null, new List<UnrealAssetPropertyInfo>());

        var accessor = cache.GetUEAssetFileAccessor(sourceFile);
        string objectName = null;
        var propertyInfos = new List<UnrealAssetPropertyInfo>();

        if (accessor.TryGetValue(linker =>
            linker.ExportMap.FirstOrDefault(e => e.IsClassDefaultObject),
            out var cdoExport) && cdoExport != null)
        {
            objectName = cdoExport.ObjectStringName;
            var properties = cdoExport.ReadProperties();
            propertyInfos = properties
                .Where(p => p.ValuePresentation != null)
                .Select(p => new UnrealAssetPropertyInfo(p.Name, p.TypeName, p.ValuePresentation))
                .ToList();
        }

        return new UnrealAssetPropertiesResponse(objectName, propertyInfos);
    }

}
