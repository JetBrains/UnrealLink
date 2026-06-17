#include "AssetRegistrySearcher.hpp"
#include "RiderAgentTools.hpp"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"

#include "Runtime/Launch/Resources/Version.h"

#include "AssetRegistry/IAssetRegistry.h"
#include "AssetRegistry/ARFilter.h"
#include "AssetRegistry/AssetData.h"
#include "Async/Async.h"
#include "Misc/PackageName.h"
#include "Misc/Paths.h"
#include "Modules/ModuleManager.h"
#include "UObject/Class.h"

// FTopLevelAssetPath (and "UObject/TopLevelAssetPath.h"), FAssetData::AssetClassPath,
// FARFilter::ClassPaths, FAssetData::IsTopLevelAsset and UClass::TryFindTypeSlow were
// all introduced in UE 5.1. On UE 4.27 and 5.0 the asset registry still exposes the
// FName-based class API (FAssetData::AssetClass / FARFilter::ClassNames) and short-name
// class lookup goes through FindObject(ANY_PACKAGE, ...).
#define RIDER_USE_TOP_LEVEL_ASSET_PATH (ENGINE_MAJOR_VERSION > 5 || (ENGINE_MAJOR_VERSION == 5 && ENGINE_MINOR_VERSION >= 1))

#if RIDER_USE_TOP_LEVEL_ASSET_PATH
#include "UObject/TopLevelAssetPath.h"
#endif

namespace
{
    // Short-name class lookup, searching the global ::UClass list (native +
    // Blueprint-generated). On UE 5.1+ this is ::UClass::TryFindTypeSlow; on
    // UE 4.27 / 5.0 it is FindObject(ANY_PACKAGE, ...), which TryFindTypeSlow
    // replaced (FindObject<UClass>(ANY_PACKAGE, ...) is deprecated in 5.1+).
    ::UClass* ResolveClassByShortName(const FString& ShortName)
    {
        if (ShortName.IsEmpty()) return nullptr;
        static const FString Prefixes[] = { TEXT(""), TEXT("U"), TEXT("A"), TEXT("F") };
        for (const FString& P : Prefixes)
        {
            const FString Candidate = P + ShortName;
#if RIDER_USE_TOP_LEVEL_ASSET_PATH
            if (::UClass* Cls = ::UClass::TryFindTypeSlow<::UClass>(Candidate))
                return Cls;
#else
            if (::UClass* Cls = FindObject<::UClass>(ANY_PACKAGE, *Candidate))
                return Cls;
#endif
        }
        return nullptr;
    }

    // Convert /Game/Foo/Bar → <ProjectAbsolute>/Content/Foo/Bar.uasset (and plugin equivalents).
    // TryConvertLongPackageNameToFilename returns a path relative to the engine's
    // working directory (e.g. ../../../../Projects/MyProject/Content/...). The cache
    // path returns absolute paths; we match that here so consumers can compare and
    // dedupe results across sources without further normalisation.
    FString PackageNameToDiskPath(const FString& PackageName)
    {
        FString DiskPath;
        if (!FPackageName::TryConvertLongPackageNameToFilename(
                PackageName, DiskPath, FPackageName::GetAssetPackageExtension()))
        {
            return PackageName;
        }
        return FPaths::ConvertRelativePathToFull(DiskPath);
    }

    // Mirror the Python prototype's logic: filter ARFilter results post-fetch
    // and project into the wire format on the game thread.
    void RunQueryOnGameThread(
        FString QueryStr,
        FString BaseClassStr,
        FString PackagePathStr,
        int32 Limit,
        rd::RdTask<JetBrains::EditorPlugin::AssetLiveSearchResponse> Task)
    {
        using namespace JetBrains::EditorPlugin;

        IAssetRegistry& AR = IAssetRegistry::GetChecked();

        FARFilter Filter;
        Filter.bRecursivePaths = true;
        Filter.bRecursiveClasses = true;
        Filter.PackagePaths.Add(FName(PackagePathStr.IsEmpty() ? TEXT("/Game") : *PackagePathStr));
        if (!BaseClassStr.IsEmpty())
        {
            if (::UClass* Cls = ResolveClassByShortName(BaseClassStr))
            {
#if RIDER_USE_TOP_LEVEL_ASSET_PATH
                Filter.ClassPaths.Add(FTopLevelAssetPath(Cls));
#else
                Filter.ClassNames.Add(Cls->GetFName());
#endif
            }
        }

        TArray<FAssetData> Found;
        AR.GetAssets(Filter, Found);

        const FString Needle = QueryStr.ToLower();
        TArray<rd::Wrapper<AssetLiveSearchAsset>> Out;
        Out.Reserve(FMath::Min(Found.Num(), Limit));
        for (const FAssetData& A : Found)
        {
            if (Out.Num() >= Limit) break;
#if RIDER_USE_TOP_LEVEL_ASSET_PATH
            // Skip runtime in-level actor instances (e.g. ChaosDebugDrawActor_UAID_…).
            // Without this, base_class="Actor" returns thousands of placed-actor
            // entries rather than the standalone .uasset/.umap files users browse for.
            // The UAID instances come from One File Per Actor, a UE 5.1+ feature, so
            // there is nothing analogous to filter out on UE 4.27 / 5.0.
            if (!A.IsTopLevelAsset()) continue;
#endif
            const FString Name = A.AssetName.ToString();
            if (!Needle.IsEmpty() && !Name.ToLower().Contains(Needle))
                continue;

#if RIDER_USE_TOP_LEVEL_ASSET_PATH
            const FString AssetClassShort = A.AssetClassPath.GetAssetName().ToString();
#else
            const FString AssetClassShort = A.AssetClass.ToString();
#endif
            Out.Emplace(AssetLiveSearchAsset(
                PackageNameToDiskPath(A.PackageName.ToString()),
                Name,
                BaseClassStr.IsEmpty()
                    ? rd::optional<FString>()
                    : rd::optional<FString>(BaseClassStr),
                AssetClassShort.IsEmpty()
                    ? rd::optional<FString>()
                    : rd::optional<FString>(AssetClassShort)));
        }
        Task.set(AssetLiveSearchResponse(MoveTemp(Out)));
    }
}

void AssetRegistrySearcher::BindTo(rd::Lifetime /*ModelLifetime*/, JetBrains::EditorPlugin::RdEditorModel const& Model)
{
    using namespace JetBrains::EditorPlugin;

    Model.get_searchAssetsLive().set(
        [](rd::Lifetime, AssetLiveSearchRequest const& Req) -> rd::RdTask<AssetLiveSearchResponse>
        {
            rd::RdTask<AssetLiveSearchResponse> Task;

            const FString QueryStr = Req.get_query().has_value() ? Req.get_query().value() : FString();
            const FString BaseClassStr = Req.get_baseClass().has_value() ? Req.get_baseClass().value() : FString();
            const FString PackagePathStr = Req.get_packagePath().has_value() ? Req.get_packagePath().value() : FString();
            const int32 Limit = FMath::Clamp(Req.get_limit(), 1, 5000);

            AsyncTask(ENamedThreads::GameThread,
                [QueryStr, BaseClassStr, PackagePathStr, Limit, Task]() mutable
                {
                    RunQueryOnGameThread(QueryStr, BaseClassStr, PackagePathStr, Limit, MoveTemp(Task));
                });

            return Task;
        });
}
