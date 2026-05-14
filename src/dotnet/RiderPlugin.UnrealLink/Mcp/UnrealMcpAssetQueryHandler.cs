using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using JetBrains.Annotations;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Reader;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Reader.Entities.Properties;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Search;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink.Mcp;

[SolutionComponent(InstantiationEx.LegacyDefault)]
public class UnrealMcpAssetQueryHandler
{
    public UnrealMcpAssetQueryHandler([NotNull] UnrealHost unrealHost, [NotNull] UE4AssetsCache assetsCache)
    {
        unrealHost.PerformModelAction(model =>
        {
            model.SearchUnrealAssets.SetAsync((lt, request) =>
            {
                try { return RdTask.Successful(SearchAssets(assetsCache, request)); }
                catch (Exception ex) { return RdTask.Faulted<UnrealAssetSearchResponse>(ex); }
            });

            model.GetBlueprintHierarchy.SetAsync((lt, request) =>
            {
                try { return RdTask.Successful(GetBlueprintHierarchy(assetsCache, request)); }
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
                    try { rdTask.Set(GetAssetProperties(assetsCache, request)); }
                    catch (Exception ex) { rdTask.Set(ex); }
                });
                return rdTask;
            });
        });
    }

    [NotNull]
    private static UnrealAssetSearchResponse SearchAssets([NotNull] UE4AssetsCache cache, [NotNull] UnrealAssetSearchRequest request)
    {
        var limit = Math.Max(1, Math.Min(request.Limit, 5000));
        var results = new List<UnrealAssetInfo>();

        if (request.BaseClass != null)
        {
            var baseClassNames = cache.GetBaseClassesByShortName(request.BaseClass);
            var query = request.Query;
            foreach (var cls in UE4SearchUtil.GetDerivedBlueprintClasses(baseClassNames, cache, uniqueNames: true))
            {
                if (results.Count >= limit) break;
                if (!cls.ContainingFile.IsValid()) continue;
                if (query != null && !cls.Name.Contains(query, StringComparison.OrdinalIgnoreCase))
                    continue;
                results.Add(new UnrealAssetInfo(cls.ContainingFile.GetLocation().FullPath, cls.Name, request.BaseClass));
            }
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
    private static UnrealBlueprintHierarchyResponse GetBlueprintHierarchy([NotNull] UE4AssetsCache cache, [NotNull] UnrealBlueprintHierarchyRequest request)
    {
        var baseClassNames = cache.GetBaseClassesByShortName(request.BaseClass);
        var blueprints = UE4SearchUtil.GetDerivedBlueprintClasses(baseClassNames, cache, uniqueNames: true)
            .Take(Math.Max(1, Math.Min(request.Limit, 5000)))
            .Where(c => c.ContainingFile.IsValid())
            .Select(c => new UnrealBlueprintInfo(c.Name, c.ContainingFile.GetLocation().FullPath))
            .ToList();
        return new UnrealBlueprintHierarchyResponse(blueprints);
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
    private static UnrealAssetPropertiesResponse GetAssetProperties([NotNull] UE4AssetsCache cache, [NotNull] UnrealAssetPropertiesRequest request)
    {
        var fileName = System.IO.Path.GetFileNameWithoutExtension(request.AssetPath);
        var sourceFile = cache.GetAssetFilesContainingWord(fileName)
            .FirstOrDefault(f => f.IsValid() && f.GetLocation().FullPath == request.AssetPath);

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
