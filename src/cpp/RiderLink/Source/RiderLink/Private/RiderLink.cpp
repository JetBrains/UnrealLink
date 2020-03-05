// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "RiderLink.h"


#include "HAL/PlatformProcess.h"
#include "Modules/ModuleManager.h"

#include "UnrealEdGlobals.h"
#include "Editor/UnrealEdEngine.h"
#include "MessageEndpointBuilder.h"

#include "EditorViewportClient.h"
#if ENGINE_MINOR_VERSION < 24
#include "ILevelViewport.h"
#else
#include "IAssetViewport.h"
#endif

#include "BlueprintProvider.h"
#include "LevelEditor.h"

#include "RdEditorProtocol/UE4Library/LogMessageInfo.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLinkModule);

IMPLEMENT_MODULE(FRiderLinkModule, RiderLink);
FRiderLinkModule::FRiderLinkModule() {}
FRiderLinkModule::~FRiderLinkModule() {}

class SetForTheScope {
public:
  SetForTheScope(bool &value) : value(value) {
    value = true;
  }
  ~SetForTheScope() {
    value = false;
  }
private:
  bool &value;
};

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
      rdConnection.unrealToBackendModel.get_play().advise(rdConnection.lifetime, [this](bool shouldPlay) {
        if (PlayFromUnreal)
          return;
        SetForTheScope s(PlayFromRider);

        if (!shouldPlay && GUnrealEd && GUnrealEd->PlayWorld) {
          GUnrealEd->RequestEndPlayMap();
        } else if (shouldPlay && GUnrealEd) {
          FLevelEditorModule& LevelEditorModule = FModuleManager::GetModuleChecked<FLevelEditorModule>( TEXT("LevelEditor") );
          auto ActiveLevelViewport = LevelEditorModule.GetFirstActiveViewport();
          if (ActiveLevelViewport.IsValid()) {
            GUnrealEd->RequestPlaySession(true, ActiveLevelViewport, false);
          } else {
            GUnrealEd->RequestPlaySession( true, nullptr, false );
          }
        }
      });
    });
    static auto MessageEndpoint = FMessageEndpoint::Builder("FAssetEditorManager").Build();
    outputDevice.onSerializeMessage.BindLambda(
        [this](const TCHAR* msg, ELogVerbosity::Type Type, const class FName& Name,
               TOptional<double> Time) {
            rdConnection.scheduler.queue([this]() {
              if (GUnrealEd && !PlayFromRider) {
                SetForTheScope s(PlayFromUnreal);
                rdConnection.unrealToBackendModel.get_play().set(GUnrealEd->PlayWorld);
              }
            });
            auto CS = FString(msg);
            if (Type != ELogVerbosity::SetColor) {
                rdConnection.scheduler.queue(
                    [this, message = FString(msg), Type, Name = Name.GetPlainNameString(),
                        Time]() mutable {
                        rd::optional<rd::DateTime> DateTime;
                        if (Time) {
                            DateTime = GetTimeNow(Time.GetValue());
                        }
                        auto MessageInfo = LogMessageInfo(Type, Name, DateTime);
                        rdConnection.unrealToBackendModel.get_unrealLog().fire(
                            UnrealLogEvent{std::move(MessageInfo), std::move(message)});
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
