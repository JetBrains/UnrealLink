#include "BlueprintProvider.hpp"

#include "AssetEditorMessages.h"
#include "BlueprintEditor.h"
#include "MessageEndpointBuilder.h"
#include "MessageEndpoint.h"
#include "Kismet2/KismetEditorUtilities.h"
#include "Runtime/Launch/Resources/Version.h"

#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
#include "Toolkits/AssetEditorManager.h"
#endif
#include "Kismet2/BlueprintEditorUtils.h"

#if ENGINE_MAJOR_VERSION < 5
#include "AssetData.h"
#else
#include "AssetRegistry/AssetData.h"
#endif

void BluePrintProvider::AddAsset(FAssetData const& AssetData) {
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
    UObject* cls = AssetData.GetAsset();
#else
    UObject* cls = AssetData.FastGetAsset();
#endif
    if (cls) {
        UBlueprint* Blueprint = Cast<UBlueprint>(cls);
        if (Blueprint && Blueprint->IsValidLowLevel()) {

        }
    }
}

bool BluePrintProvider::IsBlueprint(FString const& pathName) {
    return FPackageName::IsValidObjectPath(pathName);
}

void BluePrintProvider::OpenBlueprint(FString const& AssetPathName, FString const& AssetGuidString, TSharedPtr<FMessageEndpoint, ESPMode::ThreadSafe> const& messageEndpoint) {
    // Just to create asset manager if it wasn't created already
    FGuid AssetGuid;
    bool bIsValidGuid = FGuid::Parse(AssetGuidString, AssetGuid);
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
    FAssetEditorManager::Get();
    messageEndpoint->Publish(new FAssetEditorRequestOpenAsset(AssetPathName), EMessageScope::Process);
#else
    if (GEditor == nullptr)
        return;

    // An asset needs loading
    UPackage* Package = LoadPackage(nullptr, *AssetPathName, LOAD_NoRedirects);

    if (Package)
    {
        Package->FullyLoad();

        const FString AssetName = FPaths::GetBaseFilename(AssetPathName);
        UObject* Object = FindObject<UObject>(Package, *AssetName);
        const UBlueprint* Blueprint = Cast<UBlueprint>(Object);

        if(bIsValidGuid && Blueprint != nullptr)
        {
            UEdGraphNode* EdGraphNode = FBlueprintEditorUtils::GetNodeByGUID(Blueprint, AssetGuid);
            if(EdGraphNode != nullptr)
            {
                FKismetEditorUtilities::BringKismetToFocusAttentionOnObject(EdGraphNode);
            }
            else
            {
                FKismetEditorUtilities::BringKismetToFocusAttentionOnObject(Blueprint);
            }
        }
        else if(Object != nullptr)
        {
            GEditor->GetEditorSubsystem<UAssetEditorSubsystem>()->OpenEditorForAsset(Object);
        }
    }
#endif
}
