#pragma once
#include "CoreMinimal.h"
#include "Modules/ModuleInterface.h"
#include "lifetime/LifetimeDefinition.h"

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderAgentToolsModule, Log, All);

class FRiderAgentToolsModule final : public IModuleInterface
{
public:
    void StartupModule() override;
    void ShutdownModule() override;

private:
    rd::LifetimeDefinition ModuleLifetimeDef;
};
