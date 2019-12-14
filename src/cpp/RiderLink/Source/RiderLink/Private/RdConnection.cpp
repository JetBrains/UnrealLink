#include "RdConnection.hpp"

#include "wire/SocketWire.h"
#include "protocol/Protocol.h"

#include "Windows/AllowWindowsPlatformTypes.h"

#include "Runtime/CoreUObject/Public/UObject/Class.h"
#include "Misc/App.h"

#include "BlueprintProvider.h"
#include "AssetRegistryModule.h"
#include "Engine/Blueprint.h"
#include "RdEditorProtocol/UE4Library/UE4Library.h"
#include "ProtocolFactory.h"


RdConnection::RdConnection():
    lifetimeDef{rd::Lifetime::Eternal()}
    , socketLifetimeDef{rd::Lifetime::Eternal()}
    , lifetime{lifetimeDef.lifetime}
    , socketLifetime{socketLifetimeDef.lifetime}
    , scheduler{/*lifetime, "UnrealEditorScheduler"*/} {}

RdConnection::~RdConnection() {
    socketLifetimeDef.terminate();
    lifetimeDef.terminate();
}

void RdConnection::init() {
    _CrtDbgBreak();

    FAssetRegistryModule& AssetRegistryModule = FModuleManager::LoadModuleChecked<FAssetRegistryModule>(
        TEXT("AssetRegistry"));
    IAssetRegistry& AssetRegistry = AssetRegistryModule.Get();

    AssetRegistry.OnAssetAdded().AddLambda([](FAssetData AssetData) {
        BluePrintProvider::AddAsset(AssetData);
    });  

    scheduler.queue([this] {
        protocol = ProtocolFactory::create(scheduler, socketLifetime);

        unrealToBackendModel.connect(lifetime, protocol.Get());
        Jetbrains::EditorPlugin::UE4Library::serializersOwner.registerSerializersCore(
            unrealToBackendModel.get_serialization_context().get_serializers());
        
        unrealToBackendModel.get_navigateToBlueprintClass().advise(
            lifetime, [this](Jetbrains::EditorPlugin::BlueprintClass const& s) {
                // BluePrintProvider::OpenBlueprint(s.get_pathName(), s.get_graphName());
            });
    });

    BluePrintProvider::OnBlueprintAdded.BindLambda([this](UBlueprint* Blueprint) {
        scheduler.queue([this, Blueprint] {
            unrealToBackendModel.get_onBlueprintAdded().fire(
                Jetbrains::EditorPlugin::BlueprintClass(Blueprint->GetPathName()));
        });
    });
}

#include "Windows/HideWindowsPlatformTypes.h"
