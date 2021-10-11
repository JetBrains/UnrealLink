#pragma once

#include "RiderOutputDevice.hpp"

#include "Templates/UniquePtr.h"

#include "lifetime/LifetimeDefinition.h"

#include "Logging/LogMacros.h"
#include "Logging/LogVerbosity.h"
#include "Modules/ModuleInterface.h"
#include "scheduler/SingleThreadScheduler.h"

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderLoggingExtensionModule, Log, All);

class FRiderLoggingExtensionModule : public IModuleInterface
{
public:
    FRiderLoggingExtensionModule() = default;
    virtual ~FRiderLoggingExtensionModule() override = default;

    /** IModuleInterface implementation */
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;
    virtual bool SupportsDynamicReloading() override { return true; }

private:
    TUniquePtr<rd::SingleThreadScheduler> LoggingScheduler;
    FRiderOutputDevice OutputDevice;
    rd::LifetimeDefinition ModuleLifetimeDef;
};
