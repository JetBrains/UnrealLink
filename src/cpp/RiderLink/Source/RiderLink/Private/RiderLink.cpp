// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "RiderLink.hpp"

#include "HAL/Platform.h"
#include "Modules/ModuleManager.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLinkModule);

IMPLEMENT_MODULE(FRiderLinkModule, RiderLink);

void FRiderLinkModule::ShutdownModule()
{
  UE_LOG(FLogRiderLinkModule, Log, TEXT("SHUTDOWN START"));
  
  UE_LOG(FLogRiderLinkModule, Log, TEXT("SHUTDOWN FINISH"));
}

void FRiderLinkModule::StartupModule()
{
  UE_LOG(FLogRiderLinkModule, Log, TEXT("STARTUP START"));

  rdConnection.Init();

  UE_LOG(FLogRiderLinkModule, Log, TEXT("STARTUP FINISH"));
}

bool FRiderLinkModule::SupportsDynamicReloading() { return true; }

#undef LOCTEXT_NAMESPACE
