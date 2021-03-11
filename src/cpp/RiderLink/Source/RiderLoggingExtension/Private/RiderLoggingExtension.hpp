#pragma once

#include "RiderOutputDevice.hpp"

#include "lifetime/LifetimeDefinition.h"

#include "Logging/LogMacros.h"
#include "Logging/LogVerbosity.h"
#include "Modules/ModuleInterface.h"

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderLoggingExtensionModule, Log, All);

class FRiderLoggingExtensionModule : public IModuleInterface
{
public:
    FRiderLoggingExtensionModule() = default;
    virtual ~FRiderLoggingExtensionModule() override = default;

    /** IModuleInterface implementation */
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;
    virtual bool SupportsDynamicReloading() override { return true; };

private:
    FRiderOutputDevice outputDevice;
    rd::LifetimeDefinition ModuleLifetimeDef;
};
