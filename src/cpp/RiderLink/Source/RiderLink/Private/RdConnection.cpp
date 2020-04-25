#include "RdConnection.hpp"

// ReSharper disable once CppUnusedIncludeDirective

#include "Windows/AllowWindowsPlatformTypes.h"

#include "Toolkits/AssetEditorManager.h"
#include "AssetRegistryModule.h"
#include "IAssetRegistry.h"
#include "MessageEndpointBuilder.h"
#include "Engine/Blueprint.h"
#include "Framework/Docking/TabManager.h"
#include "Editor.h"


#include "BlueprintProvider.h"
#include "ProtocolFactory.h"
#include "RdEditorProtocol/UE4Library/UE4Library.h"

RdConnection::RdConnection():
    lifetimeDef{rd::Lifetime::Eternal()}
    , socketLifetimeDef{rd::Lifetime::Eternal()}
    , lifetime{lifetimeDef.lifetime}
    , socketLifetime{socketLifetimeDef.lifetime}
    , scheduler{socketLifetime, "UnrealEditorScheduler"} {}

RdConnection::~RdConnection() {
    socketLifetimeDef.terminate();
    lifetimeDef.terminate();
}

static void AllowSetForeGroundForEditor(Jetbrains::EditorPlugin::RdEditorModel const& unrealToBackendModel) {
    static const int32 CurrentProcessId = FPlatformProcess::GetCurrentProcessId();
    try {
        const rd::WiredRdTask<bool> Task = unrealToBackendModel.get_allowSetForegroundWindow().sync(CurrentProcessId);
        if (Task.is_faulted()) {
            std::cerr << "AllowSetForeGroundForEditor:" << rd::to_string(Task.value_or_throw());
        }
        else if (Task.is_succeeded()) {
            if (!(Task.value_or_throw().unwrap())) {
                std::cerr << "AllowSetForeGroundForEditor:" + false;
            }
        }
    }
    catch (std::exception const &e) {
        std::cerr << "AllowSetForeGroundForEditor:" + rd::to_string(e);
    }
}

void RdConnection::init() {
    const FAssetRegistryModule* AssetRegistryModule = &FModuleManager::LoadModuleChecked<FAssetRegistryModule>
        (AssetRegistryConstants::ModuleName);

    auto MessageEndpoint = FMessageEndpoint::Builder(FName("FAssetEditorManager")).Build();

    AssetRegistryModule->Get().OnAssetAdded().AddLambda([](FAssetData AssetData) {
        // TODO: Fix loading uasset's on 4.23-
        // BluePrintProvider::AddAsset(AssetData);
    });


    protocol = ProtocolFactory::create(scheduler, socketLifetime);
    
    unrealToBackendModel.connect(lifetime, protocol.Get());
    Jetbrains::EditorPlugin::UE4Library::serializersOwner.registerSerializersCore(
        unrealToBackendModel.get_serialization_context().get_serializers());

    unrealToBackendModel.get_openBlueprint().advise(
        lifetime, [this, MessageEndpoint](Jetbrains::EditorPlugin::BlueprintReference const& s) {
            try {
                AllowSetForeGroundForEditor(unrealToBackendModel);

                auto Window = FGlobalTabmanager::Get()->GetRootWindow();
                if (Window->IsWindowMinimized()) {
                    Window->Restore();
                } else {
                    Window->HACK_ForceToFront();
                }
                BluePrintProvider::OpenBlueprint(s.get_pathName(), MessageEndpoint);
            } catch (std::exception const& e) {
                std::cerr << rd::to_string(e);
            }
        });

    unrealToBackendModel.get_isBlueprintPathName().set([](FString const& pathName) -> bool {
        return BluePrintProvider::IsBlueprint(pathName);
    });


    BluePrintProvider::OnBlueprintAdded.BindLambda([this](UBlueprint* Blueprint) {
        scheduler.queue([this, Blueprint] {
            unrealToBackendModel.get_onBlueprintAdded().fire(
                Jetbrains::EditorPlugin::UClass(Blueprint->GetPathName()));
        });
    });
}

// ReSharper disable once CppUnusedIncludeDirective
#include "Windows/HideWindowsPlatformTypes.h"
