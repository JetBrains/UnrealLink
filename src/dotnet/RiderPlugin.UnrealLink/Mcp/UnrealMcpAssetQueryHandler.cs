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
        var rdTask = new RdTask<IUnrealAssetPropertiesResponse>();
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
    var matchesPackagePath = UE4AssetNameSearch.CreatePackagePathFilter(solution, request.PackagePath);

    if (request.BaseClass != null)
    {
      // One Blueprint class can be reached through several bases of the closure; dedupe by file:
      // two assets with the same name in different folders are two distinct results.
      var seen = new HashSet<VirtualFileSystemPath>();
      foreach (var shortName in BuildCppClassClosure(solution, request.BaseClass))
      {
        foreach (var baseFqn in cache.GetBaseClassesByShortName(shortName))
        {
          foreach (var cls in UE4SearchUtil.GetDerivedBlueprintClasses(baseFqn, cache))
          {
            if (results.Count >= limit) goto done;
            if (!cls.ContainingFile.IsValid()) continue;
            var location = cls.ContainingFile.GetLocation();
            // `query` means the same thing whether `baseClass` is set: the asset's file name.
            if (!UE4AssetNameSearch.NameMatches(location, request.Query)) continue;
            if (!seen.Add(location)) continue;
            if (!matchesPackagePath(location)) continue;
            results.Add(new UnrealAssetInfo(location.FullPath, location.NameWithoutExtension, request.BaseClass));
          }
        }
      }
      done: ;
    }
    else if (request.Query != null)
    {
      foreach (var location in UE4AssetNameSearch.EnumerateAssetPaths(solution))
      {
        if (results.Count >= limit) break;
        if (!UE4AssetNameSearch.NameMatches(location, request.Query)) continue;
        if (!matchesPackagePath(location)) continue;
        results.Add(new UnrealAssetInfo(location.FullPath, location.NameWithoutExtension, null));
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
  private static IUnrealAssetPropertiesResponse GetAssetProperties([NotNull] ISolution solution, [NotNull] UE4AssetsCache cache, [NotNull] UnrealAssetPropertiesRequest request)
  {
    var trimmed = request.AssetPath.Trim();
    if (string.IsNullOrEmpty(trimmed))
      return new UnrealAssetPropertiesErrorResponse("assetPath is empty. Pass the absolute .uasset/.umap path, exactly as returned by search_assets.");

    var path = VirtualFileSystemPath.TryParse(trimmed, solution.GetInteractionContext());
    if (path.IsEmpty)
      return new UnrealAssetPropertiesErrorResponse($"assetPath '{trimmed}' is not a valid file system path. Pass the absolute .uasset/.umap path, exactly as returned by search_assets.");
    var sourceFile = ResolveAssetSourceFile(solution, path);
    if (sourceFile == null)
      return new UnrealAssetPropertiesErrorResponse($"{trimmed} cannot be found in Rider's assets index." +
                                                    $" If the file does exist on disk, Rider may not have finished indexing yet," +
                                                    $" or Blueprint support may be disabled for this solution.");
    var fullPath = sourceFile.GetLocation().FullPath;

    var accessor = cache.GetUEAssetFileAccessor(sourceFile);
    // The accessor swallows every parsing exception into a UEAssetExceptionDiagnostic and only reports false,
    // so both failures below must be raised explicitly — otherwise the caller gets an empty property list
    // that is indistinguishable from an asset which genuinely has none.
    if (!accessor.IsValid())
      return new UnrealAssetPropertiesErrorResponse($"Unreal asset '{fullPath}' could not be parsed by Rider's asset reader " +
                                                    "(the parsing diagnostic is attached to the file).");

    if (!accessor.TryGetValue(ReadPrimaryExport, out var primaryExport))
      return new UnrealAssetPropertiesErrorResponse($"Reading object properties of '{fullPath}' failed inside the Unreal asset reader.");

    if (primaryExport == null)
      return new UnrealAssetPropertiesErrorResponse($"Unreal asset {fullPath} exposes no readable object");

    return new UnrealAssetPropertiesResponse(primaryExport.Value.ObjectName, primaryExport.Value.Properties);
  }

  /// <summary>
  /// Reads the export whose UPROPERTY values the caller means: the Class Default Object for Blueprint assets,
  /// otherwise the first instance export (DataAssets, native UDataAsset subclasses, …).
  /// Returns null when the asset has no such export — an empty property list is a *valid* answer and is
  /// deliberately not conflated with it.
  /// </summary>
  private static (string ObjectName, List<UnrealAssetPropertyInfo> Properties)? ReadPrimaryExport([NotNull] UELinker linker)
  {
    var export = linker.ExportMap.FirstOrDefault(e => e.IsClassDefaultObject)
                 ?? linker.ExportMap.FirstOrDefault(e => !e.IsBlueprintGeneratedClass() && !e.IsFunction());
    if (export == null) return null;

    var properties = export.ReadProperties()
      .Select(p => new UnrealAssetPropertyInfo(p.Name, p.TypeName, p.ValuePresentation ?? ""))
      .ToList();
    return (export.ObjectStringName, properties);
  }

  /// <summary>
  /// Resolves the requested asset path to the <see cref="IPsiSourceFile"/> of an indexed UE asset or returns null.
  /// <para/>
  /// `.uasset`/`.umap` files are NOT project items: <c>UE4AssetAdditionalFilesModuleFactory</c> scans the
  /// Content/Plugins directories itself and registers every asset it finds directly into the standalone
  /// <c>PsiModuleOnFileSystemPaths</c> owned by <see cref="UE4AssetFilesPsiModuleFactory"/>. That module is the
  /// primary index and must be asked by path — the same pattern as <c>UEGameplayTagsManager.ExtractTableTags</c>.
  /// <c>ISolution.FindProjectItemsByLocation</c> only ever answers for an asset that is *also* a project item
  /// (opened in the editor as a Misc File, or added to a project by a test fixture), so it stays as a secondary
  /// lookup instead of being the only one (RIDER-141607).
  /// <para/>
  /// Must be called under a read lock; the RD handler above already runs inside <c>ExecuteOrQueueReadLockEx</c>.
  /// </summary>
  [CanBeNull]
  private static IPsiSourceFile ResolveAssetSourceFile([NotNull] ISolution solution, [NotNull] VirtualFileSystemPath path)
  {
    var module = solution.GetComponent<UE4AssetFilesPsiModuleFactory>().PsiModule;
    if (module != null && module.TryGetFileByPath(path, out var indexed) && indexed != null && indexed.IsValid())
      return indexed;

    var psiModules = solution.GetPsiServices().Modules;
    foreach (var projectFile in solution.FindProjectItemsByLocation(path).OfType<IProjectFile>())
    {
      if (!projectFile.IsValid()) continue; // GetPsiSourceFilesFor asserts validity
      foreach (var candidate in psiModules.GetPsiSourceFilesFor(projectFile))
      {
        if (candidate != null && candidate.IsValid() && candidate.LanguageType.Is<UnrealAssetFileType>())
          return candidate;
      }
    }

    return null;
  }
}