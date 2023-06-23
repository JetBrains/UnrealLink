#pragma once

#include "CoreMinimal.h"
#include "Containers/Ticker.h"
#include "lifetime/LifetimeDefinition.h"
#include "Modules/ModuleManager.h"

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderLCModule, Log, All);

class FRiderLCModule : public IModuleInterface
{
public:
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;
	virtual bool SupportsDynamicReloading() override { return true; }
	void SetupLiveCodingBinds();
	
private:
	bool Tick(float X);
	
	FTickerDelegate TickDelegate;
	FTSTicker::FDelegateHandle TickDelegateHandle;
    FDelegateHandle PatchCompleteHandle;
    rd::LifetimeDefinition ModuleLifetimeDef;
};
