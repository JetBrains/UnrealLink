#include "RiderAgentTools.hpp"
#include "AssetRegistrySearcher.hpp"
#include "PythonExecutor.hpp"
#include "IRiderLink.hpp"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"
#include "Modules/ModuleManager.h"

#define LOCTEXT_NAMESPACE "RiderAgentTools"
DEFINE_LOG_CATEGORY(FLogRiderAgentToolsModule);
IMPLEMENT_MODULE(FRiderAgentToolsModule, RiderAgentTools);

void FRiderAgentToolsModule::StartupModule()
{
    UE_LOG(FLogRiderAgentToolsModule, Verbose, TEXT("STARTUP"));
    IRiderLinkModule& RiderLinkModule = IRiderLinkModule::Get();
    ModuleLifetimeDef = RiderLinkModule.CreateNestedLifetimeDefinition();
    RiderLinkModule.ViewModel(ModuleLifetimeDef.lifetime,
        [](rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model)
        {
            PythonExecutor::BindTo(ModelLifetime, Model);
            AssetRegistrySearcher::BindTo(ModelLifetime, Model);
        });
}

void FRiderAgentToolsModule::ShutdownModule()
{
    ModuleLifetimeDef.terminate();
}
