#include "BlueprintProvider.h"

#include "UObject/UObjectIterator.h"
#include "EdGraph/EdGraph.h"
#include "Toolkits/AssetEditorManager.h"
#include "BlueprintEditor.h"
#include "AssetEditorMessages.h"
#include "MessageEndpointBuilder.h"

FOnBlueprintAdded BluePrintProvider::OnBlueprintAdded{};

void BluePrintProvider::AddBlueprint(UBlueprint* Blueprint) {
    OnBlueprintAdded.ExecuteIfBound(Blueprint);   
}

void BluePrintProvider::AddAsset(FAssetData AssetData) {
    UObject* cls = AssetData.FastGetAsset();
    if (cls) {
        UBlueprint* Blueprint = Cast<UBlueprint>(cls);
        if (Blueprint && Blueprint->IsValidLowLevel()) {
            AddBlueprint(Blueprint);           
        }
    }
}

bool BluePrintProvider::IsBlueprint(FString const& Word) {
    /*for (auto it : cache) {
        auto name = it->GetName();
        if (name == Word) {
            return true;
        }
        auto FullName = it->GetFullName();
        if (FullName == Word) {
            return true;
        }
        auto PathName = it->GetPathName();
        if (PathName == Word) {
            return true;
        }
    }*/
    return false;
}

void BluePrintProvider::OpenBlueprint(FString const& path, TSharedPtr<FMessageEndpoint, ESPMode::ThreadSafe> const& messageEndpoint) {
    messageEndpoint->Publish(new FAssetEditorRequestOpenAsset(path), EMessageScope::Process);
}
