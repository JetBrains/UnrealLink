#pragma once

#include "lifetime/LifetimeDefinition.h"

#include "Logging/LogMacros.h"
#include "Logging/LogVerbosity.h"
#include "MessageEndpoint.h"
#include "Modules/ModuleInterface.h"
#include "Templates/SharedPointer.h"

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderBlueprintExtensionModule, Log, All);

class FRiderBlueprintExtensionModule : public IModuleInterface
{
public:
    FRiderBlueprintExtensionModule() = default;
    virtual ~FRiderBlueprintExtensionModule() override = default;

    /** IModuleInterface implementation */
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;
    virtual bool SupportsDynamicReloading() override { return true; };
private:
    TSharedPtr<FMessageEndpoint, ESPMode::ThreadSafe> MessageEndpoint;
    rd::LifetimeDefinition ModuleLifetimeDef;
};
