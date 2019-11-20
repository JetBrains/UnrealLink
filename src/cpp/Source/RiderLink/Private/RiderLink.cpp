// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "RiderLink.h"

#include "UE4TypesMarshallers.h"

#include "Modules/ModuleManager.h"
#include "HAL/PlatformProcess.h"

#include "UnrealEdGlobals.h"
#include "RdEditorProtocol/UE4Library/UnrealLogMessage.h"
#include "Editor/UnrealEdEngine.h"

#include "BlueprintProvider.h"

#include "rd_core_cpp/types/DateTime.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLinkModule);

IMPLEMENT_MODULE(FRiderLinkModule, RiderLink);
FRiderLinkModule::FRiderLinkModule(){}
FRiderLinkModule::~FRiderLinkModule(){}

void FRiderLinkModule::ShutdownModule()
{
}

void FRiderLinkModule::StartupModule()
{
	rdConnection.init();

	UE_LOG(FLogRiderLinkModule, Warning, TEXT("INIT START"));
	rdConnection.scheduler.queue([this] {
		rdConnection.unrealToBackendModel.get_play().advise(rdConnection.lifetime, [](bool shouldPlay) {
			GUnrealEd->PlayWorld->bDebugPauseExecution = shouldPlay;
		});
	});
	static const auto START_TIME = FDateTime::Now();

	outputDevice.onSerializeMessage.BindLambda([this](const TCHAR * msg, ELogVerbosity::Type Type, const class FName& Name, TOptional<double> Time){
		rdConnection.scheduler.queue([this, msg = FString(msg), Type, Name = Name.GetPlainNameString(), Time]() mutable {
			rd::optional<rd::DateTime> DateTime;
			if (Time) {
				DateTime = rd::DateTime(START_TIME.ToUnixTimestamp() + static_cast<int64>(Time.GetValue()));
			}
			rdConnection.unrealToBackendModel.get_unrealLog().fire(Jetbrains::EditorPlugin::UnrealLogMessage(msg, Type, Name, DateTime));	
		});
	});
	UE_LOG(FLogRiderLinkModule, Warning, TEXT("INIT FINISH"));
}

bool FRiderLinkModule::SupportsDynamicReloading()
{
	return true;
}

#undef LOCTEXT_NAMESPACE
