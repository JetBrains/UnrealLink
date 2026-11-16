#pragma once

#include "lifetime/LifetimeDefinition.h"

#include "Containers/Ticker.h"
#include "Containers/UnrealString.h"
#include "Logging/LogMacros.h"
#include "Logging/LogVerbosity.h"
#include "MessageEndpoint.h"
#include "Modules/ModuleInterface.h"
#include "Templates/SharedPointer.h"
#include "Runtime/Launch/Resources/Version.h"

#define RIDERBLUEPRINT_USES_MPSC_QUEUE (ENGINE_MAJOR_VERSION > 5 || (ENGINE_MAJOR_VERSION == 5 && ENGINE_MINOR_VERSION >= 1))

#if RIDERBLUEPRINT_USES_MPSC_QUEUE
#include "Containers/MpscQueue.h"
#else
#include "Containers/Queue.h"
#endif

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderBlueprintModule, Log, All);

class FRiderBlueprintModule : public IModuleInterface
{
public:
    FRiderBlueprintModule() = default;
    virtual ~FRiderBlueprintModule() override = default;

    /** IModuleInterface implementation */
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;
    virtual bool SupportsDynamicReloading() override { return true; };
private:
    struct FPendingOpen
    {
        FString AssetPathName;
        FString AssetGuid;
    };

    bool DrainPendingOpens(float DeltaTime);

    TSharedPtr<FMessageEndpoint, ESPMode::ThreadSafe> MessageEndpoint;
    rd::LifetimeDefinition ModuleLifetimeDef;

#if RIDERBLUEPRINT_USES_MPSC_QUEUE
    TMpscQueue<FPendingOpen> PendingOpens;
#else
    TQueue<FPendingOpen, EQueueMode::Mpsc> PendingOpens;
#endif

#if ENGINE_MAJOR_VERSION >= 5
    FTSTicker::FDelegateHandle OpenTicker;
#else
    FDelegateHandle OpenTicker;
#endif
};
