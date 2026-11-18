#pragma once

#include "CoreMinimal.h"
#include "Modules/ModuleManager.h"

class FPromptGestureProcessor;

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderAgentPromptModule, Log, All);

class FRiderAgentPromptModule : public IModuleInterface
{
public:
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;

private:
    TSharedPtr<FPromptGestureProcessor> PromptGestureProcessor;
};
