#include "RiderBlueprint.hpp"
#include "RiderLogMacros.h"

#include "BlueprintProvider.hpp"
#include "IRiderLink.hpp"
#include "Model/RdEditorProtocol/RdEditorModel/RdEditorModel.Pregenerated.h"


#include "Engine/Blueprint.h"
#include "Framework/Docking/TabManager.h"
#include "HAL/PlatformProcess.h"
#include "MessageEndpoint.h"
#include "MessageEndpointBuilder.h"
#include "Modules/ModuleManager.h"
#include "Runtime/Launch/Resources/Version.h"

#if ENGINE_MAJOR_VERSION < 5
#include "AssetRegistryModule.h"
#else
#include "AssetRegistry/AssetRegistryModule.h"
#endif

#define LOCTEXT_NAMESPACE "RiderBlueprint"

DEFINE_LOG_CATEGORY(FLogRiderBlueprintModule);

IMPLEMENT_MODULE(FRiderBlueprintModule, RiderBlueprint);

static void AllowSetForeGroundForEditor(JetBrains::EditorPlugin::RdEditorModel const & unrealToBackendModel) {
    static const int32 CurrentProcessId = FPlatformProcess::GetCurrentProcessId();
    try {
        const rd::WiredRdTask<bool> Task = unrealToBackendModel.get_allowSetForegroundWindow().sync(CurrentProcessId);
        if (Task.is_faulted()) {
            RIDERLINK_LOG(FLogRiderBlueprintModule, Error, "AllowSetForeGroundForEditor failed: %hs ", rd::to_string(Task.value_or_throw()).c_str());
        }
        else if (Task.is_succeeded()) {
            if (!(Task.value_or_throw().unwrap())) {
                RIDERLINK_LOG(FLogRiderBlueprintModule, Error, "AllowSetForeGroundForEditor failed: %hs ", rd::to_string(Task.value_or_throw()).c_str());
            }
        }
    }
    catch (std::exception const &e) {
        RIDERLINK_LOG(FLogRiderBlueprintModule, Error, "AllowSetForeGroundForEditor failed: %hs ", rd::to_string(e).c_str());
    }
}

bool FRiderBlueprintModule::DrainPendingOpens(float /*DeltaTime*/)
{
    if (PendingOpens.IsEmpty())
        return true;

    auto Window = FGlobalTabmanager::Get()->GetRootWindow();
    if (!Window.IsValid()) return true;

    if (Window->IsWindowMinimized())
    {
        Window->Restore();
    }
    else
    {
        Window->HACK_ForceToFront();
    }

    FPendingOpen Open;
    while (PendingOpens.Dequeue(Open))
    {
        BluePrintProvider::OpenBlueprint(Open.AssetPathName, Open.AssetGuid, MessageEndpoint);
    }
    return true;
}

void FRiderBlueprintModule::StartupModule()
{
    RIDERLINK_LOG(FLogRiderBlueprintModule, Verbose, "STARTUP START");
    IRiderLinkModule& RiderLinkModule = IRiderLinkModule::Get();
    ModuleLifetimeDef = RiderLinkModule.CreateNestedLifetimeDefinition();

    const FAssetRegistryModule* AssetRegistryModule = &FModuleManager::LoadModuleChecked<FAssetRegistryModule>
        (AssetRegistryConstants::ModuleName);

    MessageEndpoint = FMessageEndpoint::Builder(FName("FAssetEditorManager")).Build();

#if ENGINE_MAJOR_VERSION >= 5
    OpenTicker = FTSTicker::GetCoreTicker().AddTicker(FTickerDelegate::CreateRaw(this, &FRiderBlueprintModule::DrainPendingOpens));
#else
    OpenTicker = FTicker::GetCoreTicker().AddTicker(FTickerDelegate::CreateRaw(this, &FRiderBlueprintModule::DrainPendingOpens));
#endif

    AssetRegistryModule->Get().OnAssetAdded().AddLambda([](const FAssetData& AssetData) {
        // TO-DO: Fix loading uasset's on 4.23-
        // BluePrintProvider::AddAsset(AssetData);
    });

    RiderLinkModule.ViewModel(ModuleLifetimeDef.lifetime, [this] (rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& UnrealToBackendModel)
    {
        UnrealToBackendModel.get_openBlueprint().advise(
            ModelLifetime,
            [this, &UnrealToBackendModel](
            JetBrains::EditorPlugin::BlueprintReference const& s)
            {
                try
                {
                    AllowSetForeGroundForEditor(UnrealToBackendModel);
                    PendingOpens.Enqueue(FPendingOpen{ s.get_pathName(), s.get_guid() });
                }
                catch (std::exception const& e)
                {
                    std::cerr << rd::to_string(e);
                }
            }
        );

        UnrealToBackendModel.get_isBlueprintPathName().set([](FString const& pathName) -> bool
        {
            return BluePrintProvider::IsBlueprint(pathName);
        });
    });
    RIDERLINK_LOG(FLogRiderBlueprintModule, Verbose, "STARTUP FINISH");
}

void FRiderBlueprintModule::ShutdownModule()
{
    RIDERLINK_LOG(FLogRiderBlueprintModule, Verbose, "SHUTDOWN START");
    ModuleLifetimeDef.terminate();
#if ENGINE_MAJOR_VERSION >= 5
    FTSTicker::RemoveTicker(OpenTicker);
#else
    FTicker::GetCoreTicker().RemoveTicker(OpenTicker);
#endif
    OpenTicker.Reset();
    RIDERLINK_LOG(FLogRiderBlueprintModule, Verbose, "SHUTDOWN FINISH");
}

#undef LOCTEXT_NAMESPACE