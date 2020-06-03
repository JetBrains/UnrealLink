#pragma once

#include "Delegates/Delegate.h"

struct FAssetData;
class FMessageEndpoint;
class UBlueprint;
DECLARE_DELEGATE_OneParam(FOnBlueprintAdded, UBlueprint *);

class RIDERBLUEPRINTEXTENSION_API BluePrintProvider {
    static void AddBlueprint(UBlueprint* Blueprint);
public:
    static FOnBlueprintAdded OnBlueprintAdded;

    static void AddAsset(FAssetData const& AssetData);

    static bool IsBlueprint(FString const& pathName);

    static void OpenBlueprint(FString const& path, TSharedPtr<FMessageEndpoint, ESPMode::ThreadSafe> const& messageEndpoint);
};
