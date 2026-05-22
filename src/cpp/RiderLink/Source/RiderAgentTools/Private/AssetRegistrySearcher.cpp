#include "AssetRegistrySearcher.hpp"
#include "RiderAgentTools.hpp"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"

#include "AssetRegistry/IAssetRegistry.h"
#include "AssetRegistry/ARFilter.h"
#include "AssetRegistry/AssetData.h"
#include "Async/Async.h"
#include "Misc/PackageName.h"
#include "Modules/ModuleManager.h"
#include "UObject/Class.h"
#include "UObject/TopLevelAssetPath.h"

namespace
{
    // UClass::TryFindTypeSlow is the cross-version short-name lookup.
    // FindObject<UClass>(ANY_PACKAGE, ...) is deprecated in UE 5.1+; this
    // template-based API searches the global UClass list (native +
    // Blueprint-generated) and works on UE 5.1 through 5.8.
    UClass* ResolveClassByShortName(const FString& ShortName)
    {
        if (ShortName.IsEmpty()) return nullptr;
        static const FString Prefixes[] = { TEXT(""), TEXT("U"), TEXT("A"), TEXT("F") };
        for (const FString& P : Prefixes)
        {
            const FString Candidate = P + ShortName;
            if (UClass* Cls = UClass::TryFindTypeSlow<UClass>(Candidate))
                return Cls;
        }
        return nullptr;
    }

    // Convert /Game/Foo/Bar → <Project>/Content/Foo/Bar.uasset (and plugin equivalents).
    FString PackageNameToDiskPath(const FString& PackageName)
    {
        FString DiskPath;
        if (FPackageName::TryConvertLongPackageNameToFilename(
                PackageName, DiskPath, FPackageName::GetAssetPackageExtension()))
        {
            return DiskPath;
        }
        return PackageName;
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
            if (UClass* Cls = ResolveClassByShortName(BaseClassStr))
                Filter.ClassPaths.Add(FTopLevelAssetPath(Cls));
        }

        TArray<FAssetData> Found;
        AR.GetAssets(Filter, Found);

        const FString Needle = QueryStr.ToLower();
        TArray<rd::Wrapper<AssetLiveSearchAsset>> Out;
        Out.Reserve(FMath::Min(Found.Num(), Limit));
        for (const FAssetData& A : Found)
        {
            if (Out.Num() >= Limit) break;
            // Skip runtime in-level actor instances (e.g. ChaosDebugDrawActor_UAID_…).
            // Without this, base_class="Actor" returns thousands of placed-actor
            // entries rather than the standalone .uasset/.umap files users browse for.
            if (!A.IsTopLevelAsset()) continue;
            const FString Name = A.AssetName.ToString();
            if (!Needle.IsEmpty() && !Name.ToLower().Contains(Needle))
                continue;

            const FString AssetClassShort = A.AssetClassPath.GetAssetName().ToString();
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
