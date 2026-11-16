#pragma once

#include "Delegates/Delegate.h"

struct FAssetData;
class FMessageEndpoint;
class UBlueprint;

class RIDERBLUEPRINT_API BluePrintProvider {
public:

    static void AddAsset(FAssetData const& AssetData);

    static bool IsBlueprint(FString const& pathName);

    // Opens the Blueprint editor on the calling thread
    static void OpenBlueprint(FString const& AssetPathName, FString const& AssetGuidString, TSharedPtr<FMessageEndpoint, ESPMode::ThreadSafe> const& messageEndpoint);
};
