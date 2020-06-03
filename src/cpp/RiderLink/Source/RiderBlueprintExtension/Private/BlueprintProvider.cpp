#include "BlueprintProvider.hpp"

#include "Async/Async.h"
#include "AssetData.h"
#include "AssetEditorMessages.h"
#include "BlueprintEditor.h"
#include "MessageEndpointBuilder.h"
#include "MessageEndpoint.h"
#include "Toolkits/AssetEditorManager.h"

#include "Runtime/Launch/Resources/Version.h"

FOnBlueprintAdded BluePrintProvider::OnBlueprintAdded{};

void BluePrintProvider::AddBlueprint(UBlueprint* Blueprint) {
    OnBlueprintAdded.ExecuteIfBound(Blueprint);
}

void BluePrintProvider::AddAsset(FAssetData const& AssetData) {
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
    UObject* cls = AssetData.GetAsset();
#else
    UObject* cls = AssetData.FastGetAsset();
#endif
    if (cls) {
        UBlueprint* Blueprint = Cast<UBlueprint>(cls);
        if (Blueprint && Blueprint->IsValidLowLevel()) {
            AddBlueprint(Blueprint);
        }
    }
}

bool BluePrintProvider::IsBlueprint(FString const& pathName) {
    return FPackageName::IsValidObjectPath(pathName);
}

void BluePrintProvider::OpenBlueprint(FString const& path, TSharedPtr<FMessageEndpoint, ESPMode::ThreadSafe> const& messageEndpoint) {
    // Just to create asset manager if it wasn't created already
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
    FAssetEditorManager::Get();
    messageEndpoint->Publish(new FAssetEditorRequestOpenAsset(path), EMessageScope::Process);
#else
    AsyncTask(ENamedThreads::GameThread, [path]()
    {
        GEditor->GetEditorSubsystem<UAssetEditorSubsystem>()->OpenEditorForAsset(path);
    });
#endif
}
