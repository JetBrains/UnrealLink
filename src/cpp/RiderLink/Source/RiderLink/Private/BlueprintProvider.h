#pragma once

#include "AssetData.h"

DECLARE_DELEGATE_OneParam(FOnBlueprintAdded, UBlueprint *);

class BluePrintProvider {
    static void AddBlueprint(UBlueprint* Blueprint);
public:
    static FOnBlueprintAdded OnBlueprintAdded;

    static void AddAsset(FAssetData AssetData);

    static bool IsBlueprint(FString const& Word);

    static void OpenBlueprint(FString const& path, FString const& name);
};
