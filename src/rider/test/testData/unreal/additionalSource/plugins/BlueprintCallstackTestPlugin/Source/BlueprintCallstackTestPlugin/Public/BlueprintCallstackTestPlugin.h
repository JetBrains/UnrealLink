#pragma once

#include "CoreMinimal.h"
#include "Modules/ModuleManager.h"

class FBlueprintCallstackTestPluginModule : public IModuleInterface
{
public:
	virtual void StartupModule() override;
	virtual void ShutdownModule() override;

private:
	/** Creates a Blueprint with one function that calls UBlueprintCallstackTestLibrary::CalledFromBlueprint, then calls that function. */
	void RunGeneratedBlueprint();

	FDelegateHandle PostEngineInitHandle;
};
