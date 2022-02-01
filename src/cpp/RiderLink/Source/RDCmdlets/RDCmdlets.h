#pragma once

#include "Modules/ModuleInterface.h"

class FRDCmdletsModule : public IModuleInterface
{
public:
	virtual void StartupModule() override {}
	virtual void ShutdownModule() override {}
	virtual bool SupportsDynamicReloading() override { return true; }
};