// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "RiderLink.h"


#include "HAL/PlatformProcess.h"
#include "Modules/ModuleManager.h"

#include "UnrealEdGlobals.h"
#include "Editor/UnrealEdEngine.h"
#include "MessageEndpointBuilder.h"

#include "LogParser.h"
#include "BlueprintProvider.h"

#include "RdEditorProtocol/UE4Library/LogMessageInfo.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLinkModule);

IMPLEMENT_MODULE(FRiderLinkModule, RiderLink);
FRiderLinkModule::FRiderLinkModule() {}
FRiderLinkModule::~FRiderLinkModule() {}

void FRiderLinkModule::ShutdownModule() {}

void FRiderLinkModule::StartupModule() {
    using namespace Jetbrains::EditorPlugin;

    static const auto START_TIME = FDateTime::Now();

    static const auto GetTimeNow = [](double Time) -> rd::DateTime {
        return rd::DateTime(static_cast<std::time_t>(START_TIME.ToUnixTimestamp() + static_cast<int64>(Time)));
    };

    rdConnection.init();

    UE_LOG(FLogRiderLinkModule, Warning, TEXT("INIT START"));
    rdConnection.scheduler.queue([this] {
        rdConnection.unrealToBackendModel.get_play().advise(rdConnection.lifetime, [](bool shouldPlay) {
            GUnrealEd->PlayWorld->bDebugPauseExecution = shouldPlay;
        });
    });
    static auto MessageEndpoint = FMessageEndpoint::Builder("FAssetEditorManager").Build();
    outputDevice.onSerializeMessage.BindLambda(
        [this](const TCHAR* msg, ELogVerbosity::Type Type, const class FName& Name,
               TOptional<double> Time) {
            auto CS = FString(msg);
            if (CS.StartsWith("!!!")) {
                BluePrintProvider::OpenBlueprint(CS.Mid(4), MessageEndpoint);
            }
            if (Type != ELogVerbosity::SetColor) {
                rdConnection.scheduler.queue(
                    [this, message = FString(msg), Type, Name = Name.GetPlainNameString(),
                        Time]() mutable {
                        rd::optional<rd::DateTime> DateTime;
                        if (Time) {
                            DateTime = GetTimeNow(Time.GetValue());
                        }
                        auto MessageInfo = LogMessageInfo(Type, Name, DateTime);
                        auto Event = LogParser::GetParts(std::move(message));
                        rdConnection.unrealToBackendModel.get_unrealLog().fire(
                            UnrealLogEvent(std::move(MessageInfo), std::move(Event)));
                    });
            }
        });


    UE_LOG(FLogRiderLinkModule, Warning, TEXT("INIT FINISH"));
    // FDebug::DumpStackTraceToLog();
}

bool FRiderLinkModule::SupportsDynamicReloading() {
    return true;
}

#undef LOCTEXT_NAMESPACE
