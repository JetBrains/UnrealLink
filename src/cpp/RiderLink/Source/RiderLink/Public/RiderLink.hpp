// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#pragma once

#include "RdConnection.hpp"

#include "Logging/LogMacros.h"
#include "Logging/LogVerbosity.h"
#include "Modules/ModuleInterface.h"

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderLinkModule, Log, All);

class FRiderLinkModule : public IModuleInterface
{
public:
	FRiderLinkModule() = default;
	~FRiderLinkModule() = default;

	static FName GetModuleName()
	{
		static const FName ModuleName = TEXT("RiderLink");
		return ModuleName;
	}

	/** IModuleInterface implementation */
	virtual void StartupModule() override;
	virtual void ShutdownModule() override;
	virtual bool SupportsDynamicReloading() override;

	RdConnection rdConnection;
};
