// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "RiderLink.h"

#include "Modules/ModuleManager.h"
#include "HAL/PlatformProcess.h"

#include "UnrealEdGlobals.h"
#include "Editor/UnrealEdEngine.h"

#include "rd_core_cpp/types/DateTime.h"
#include "LogParser.h"
#include "RdEditorProtocol/UE4Library/LogMessageEvent.h"
#include "RdEditorProtocol/UE4Library/ScriptCallStackEvent.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLinkModule);

IMPLEMENT_MODULE(FRiderLinkModule, RiderLink);
FRiderLinkModule::FRiderLinkModule() {}
FRiderLinkModule::~FRiderLinkModule() {}

void FRiderLinkModule::ShutdownModule() {}



void FRiderLinkModule::StartupModule() {
    static const auto START_TIME = FDateTime::Now();
    static int LINE_NUMBER = 0;

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

    outputDevice.onSerializeMessage.BindLambda(
        [this](const TCHAR* msg, ELogVerbosity::Type Type, const class FName& Name,
               TOptional<double> Time) {
            if (Type != ELogVerbosity::SetColor) {
                rdConnection.scheduler.queue(
                    [this, message = FString(msg), Type, Name = Name.GetPlainNameString(),
                        Time]() mutable {
                        rd::optional<rd::DateTime> DateTime;
                        if (Time) {
                            DateTime = GetTimeNow(Time.GetValue());
                        }

                        TUniquePtr<Jetbrains::EditorPlugin::LogEvent> Event;
                        auto OptGetScriptCallStack = LogParser::TryGetScriptCallStack(message);
                        if (OptGetScriptCallStack) {
                            Jetbrains::EditorPlugin::IScriptCallStack* Px = OptGetScriptCallStack.Release();
                            rd::Wrapper<Jetbrains::EditorPlugin::IScriptCallStack> CallStack;
                            // CallStack.reset(Px);
                            //todo
                            Event = MakeUnique<Jetbrains::EditorPlugin::ScriptCallStackEvent>(CallStack, 0);
                        }
                        else {
                            auto Message = Jetbrains::EditorPlugin::LogMessage(
                                message, Type, Name, DateTime);
                            Event = MakeUnique<Jetbrains::EditorPlugin::LogMessageEvent>(
                                Jetbrains::EditorPlugin::LogMessageEvent(std::move(Message), 0));
                        }
                        rdConnection.unrealToBackendModel.get_unrealLog().fire(*Event);
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
