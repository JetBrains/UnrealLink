// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "RiderLink.h"

#include "Modules/ModuleManager.h"
#include "Features/IModularFeatures.h"
#include "HAL/PlatformProcess.h"

#include "UnrealEd.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLinkModule);

IMPLEMENT_MODULE(FRiderLinkModule, RiderLink);
FRiderLinkModule::FRiderLinkModule(){}
FRiderLinkModule::~FRiderLinkModule(){}

void FRiderLinkModule::ShutdownModule()
{
	// Unbind provider from editor
	IModularFeatures::Get().UnregisterModularFeature(TEXT("SourceCodeAccessor"), &RiderSourceCodeAccessor);
}

void FRiderLinkModule::StartupModule()
{
	rdConnection.init();

	UE_LOG(FLogRiderLinkModule, Warning, TEXT("INIT START"));
	rdConnection.unrealToBackendModel.get_play().advise(rdConnection.lifetime, [](bool shouldPlay) {
		GUnrealEd->PlayWorld->bDebugPauseExecution = shouldPlay;
	});
	
	outputDevice.onSerializeMessage.BindLambda([this](const TCHAR * msg){
		rdConnection.unrealToBackendModel.get_unreal_log().fire(msg);
	});
	// Quick forced check of availability before anyone touches the module
	RiderSourceCodeAccessor.RefreshAvailability();

	// Bind our source control provider to the editor
	IModularFeatures::Get().RegisterModularFeature(TEXT("SourceCodeAccessor"), &RiderSourceCodeAccessor);
	UE_LOG(FLogRiderLinkModule, Warning, TEXT("INIT FINISH"));
}

bool FRiderLinkModule::SupportsDynamicReloading()
{
	return true;
}

FRiderSourceCodeAccessor& FRiderLinkModule::GetAccessor()
{
	return RiderSourceCodeAccessor;
}

#undef LOCTEXT_NAMESPACE
