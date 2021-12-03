#pragma once

#include "Modules/ModuleInterface.h"

class RIDERSHADERINFODUMP_API FRiderShaderInfoDump : public IModuleInterface
{
public:
	virtual void StartupModule() override;
};
