#include "RdConnection.hpp"


#include "wire/SocketWire.h"

// ReSharper disable once CppUnusedIncludeDirective
#include "Windows/AllowWindowsPlatformTypes.h"

#include "AssetEditorManager.h"
#include "AssetRegistryModule.h"
#include "BlueprintProvider.h"
#include "Engine/Blueprint.h"
#include "IAssetRegistry.h"
#include "MessageEndpointBuilder.h"
#include "ProtocolFactory.h"
#include "RdEditorProtocol/UE4Library/UE4Library.h"

RdConnection::RdConnection():
    lifetimeDef{rd::Lifetime::Eternal()}
    , socketLifetimeDef{rd::Lifetime::Eternal()}
    , lifetime{lifetimeDef.lifetime}
    , socketLifetime{socketLifetimeDef.lifetime}
    , scheduler{lifetime, "UnrealEditorScheduler"} {}

RdConnection::~RdConnection() {
    socketLifetimeDef.terminate();
    lifetimeDef.terminate();
}


void RdConnection::init() {
    const FAssetRegistryModule* AssetRegistryModule = &FModuleManager::LoadModuleChecked<FAssetRegistryModule>
        (AssetRegistryConstants::ModuleName);

    auto MessageEndpoint = FMessageEndpoint::Builder(FName("FAssetEditorManager")).Build();
    
    AssetRegistryModule->Get().OnAssetAdded().AddLambda([](FAssetData AssetData) {
        BluePrintProvider::AddAsset(AssetData);
    });


    scheduler.queue([this, MessageEndpoint, AssetRegistryModule] {
        protocol = ProtocolFactory::create(scheduler, socketLifetime);

        unrealToBackendModel.connect(lifetime, protocol.Get());
        Jetbrains::EditorPlugin::UE4Library::serializersOwner.registerSerializersCore(
            unrealToBackendModel.get_serialization_context().get_serializers());


        unrealToBackendModel.get_navigateToBlueprintClass().advise(
            lifetime, [this, MessageEndpoint](Jetbrains::EditorPlugin::BlueprintClass const& s) {
                BluePrintProvider::OpenBlueprint(s.get_pathName(), MessageEndpoint);
            });

        unrealToBackendModel.get_isBlueprintPathName().set([AssetRegistryModule](FString const& pathName) -> bool {
            // const auto AssetByObjectPath = AssetRegistryModule->Get().GetAssetByObjectPath(FName(*pathName));
            // auto bIsValid = AssetByObjectPath.IsValid();
            // return bIsValid;
            return FPackageName::IsValidObjectPath(pathName);
        });
    });

    BluePrintProvider::OnBlueprintAdded.BindLambda([this](UBlueprint* Blueprint) {
        scheduler.queue([this, Blueprint] {
            unrealToBackendModel.get_onBlueprintAdded().fire(
                Jetbrains::EditorPlugin::BlueprintClass(Blueprint->GetPathName()));
        });
    });
}

// ReSharper disable once CppUnusedIncludeDirective
#include "Windows/HideWindowsPlatformTypes.h"
