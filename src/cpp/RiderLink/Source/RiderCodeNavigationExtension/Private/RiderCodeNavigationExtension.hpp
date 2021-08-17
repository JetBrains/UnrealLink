#pragma once

#include "RiderSourceCodeNavigationHandler.h"

#include "lifetime/LifetimeDefinition.h"

#include "Logging/LogMacros.h"
#include "Logging/LogVerbosity.h"
#include "Modules/ModuleInterface.h"

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderCodeNavigationExtensionModule, Log, All);

class RIDERCODENAVIGATIONEXTENSION_API FRiderCodeNavigationExtensionModule : public IModuleInterface
{
public:
    /** IModuleInterface implementation */
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;
    virtual bool SupportsDynamicReloading() override { return true; };

private:
    FRiderSourceCodeNavigationHandler RiderSourceCodeNavigationHandler;
};
