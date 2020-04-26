// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "RiderLink.h"

#include "HAL/PlatformProcess.h"
#include "Modules/ModuleManager.h"

#include "Editor/UnrealEdEngine.h"
#include "Kismet2/DebuggerCommands.h"
#include "MessageEndpointBuilder.h"
#include "UnrealEdGlobals.h"

#include "EditorViewportClient.h"
#if ENGINE_MINOR_VERSION < 24
#include "ILevelViewport.h"
#include "LevelEditorViewport.h"
#else
#include "IAssetViewport.h"
#endif

#include "BlueprintProvider.h"
#include "IHeadMountedDisplay.h"
#include "IXRTrackingSystem.h"
#include "LevelEditor.h"
#include "Components/CapsuleComponent.h"
#include "GameFramework/PlayerStart.h"

#include "RdEditorProtocol/UE4Library/LogMessageInfo.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLinkModule);

IMPLEMENT_MODULE(FRiderLinkModule, RiderLink);
FRiderLinkModule::FRiderLinkModule() {}
FRiderLinkModule::~FRiderLinkModule() {}

class SetForTheScope {
public:
  SetForTheScope(bool &value) : value(value) { value = true; }
  ~SetForTheScope() { value = false; }

private:
  bool &value;
};

void FRiderLinkModule::ShutdownModule() {}

static int NumberOfPlayers(int mode) { return (mode & 3) + 1; }

static bool SpawnAtPlayerStart(int mode) { return (mode & 4) != 0; }

static bool DedicatedServer(int mode) { return (mode & 8) != 0; }

static EPlayModeType PlayModeFromInt(int modeNumber) {
  switch (modeNumber) {
  default: break;
  case 1: return PlayMode_InMobilePreview;
  case 2: return PlayMode_InEditorFloating;
  case 3: return PlayMode_InVR;
  case 4: return PlayMode_InNewProcess;
  case 5: return PlayMode_Simulate;
  case 6: return PlayMode_InVulkanPreview;
  }
  return PlayMode_InViewPort;
}

static int PlayModeToInt(EPlayModeType modeType) {
  switch (modeType) {
  default: break;
  case PlayMode_InTargetedMobilePreview:
  case PlayMode_InMobilePreview:
    return 1;
  case PlayMode_InEditorFloating: return 2;
  case PlayMode_InVR: return 3;
  case PlayMode_InNewProcess: return 4;
  case PlayMode_Simulate: return 5;
  case PlayMode_InVulkanPreview: return 6;
  }
  return 0;
}

FSlateApplication *slateApplication = nullptr;

static void RequestPlay(int mode) {
  FLevelEditorModule &LevelEditorModule =
      FModuleManager::GetModuleChecked<FLevelEditorModule>(
          TEXT("LevelEditor"));
  auto ActiveLevelViewport =
      LevelEditorModule.GetFirstActiveViewport();
  ULevelEditorPlaySettings *PlayInSettings =
      GetMutableDefault<ULevelEditorPlaySettings>();
  EPlayModeType playMode = PlayModeFromInt((mode & (16 + 32 + 64)) >> 4);
  const bool atPlayerStart = SpawnAtPlayerStart(mode);
  if (PlayInSettings) {
    PlayInSettings->SetPlayNumberOfClients(NumberOfPlayers(mode));
    PlayInSettings->SetPlayNetDedicated(DedicatedServer(mode));
    PlayInSettings->LastExecutedPlayModeLocation =
        atPlayerStart
          ? PlayLocation_DefaultPlayerStart
          : PlayLocation_CurrentCameraLocation;
    PlayInSettings->LastExecutedPlayModeType = playMode;
  }

  const FVector *StartLocation = nullptr;
  const FRotator *StartRotation = nullptr;
  if (!atPlayerStart && slateApplication && ActiveLevelViewport.IsValid() &&
      slateApplication->FindWidgetWindow(ActiveLevelViewport->AsWidget()).IsValid()) {
#if ENGINE_MINOR_VERSION < 24
    StartLocation = &ActiveLevelViewport->GetLevelViewportClient().GetViewLocation();
    StartRotation = &ActiveLevelViewport->GetLevelViewportClient().GetViewRotation();
#else
    StartLocation = &ActiveLevelViewport->GetAssetViewportClient().GetViewLocation();
    StartRotation = &ActiveLevelViewport->GetAssetViewportClient().GetViewRotation();
#endif
  }
  
  if (playMode == PlayMode_InEditorFloating) {
    GUnrealEd->RequestPlaySession(atPlayerStart, nullptr, false, StartLocation);
  } else if (playMode == PlayMode_InVR) {
    const bool bHMDIsReady = GEngine && GEngine->XRSystem.IsValid() && GEngine->XRSystem->GetHMDDevice() && GEngine->XRSystem->GetHMDDevice()->IsHMDConnected();
    GUnrealEd->RequestPlaySession(atPlayerStart,
                                  ActiveLevelViewport,
                                  false, StartLocation,
                                  StartRotation, -1,
                                  false,
                                  bHMDIsReady);
  } else if (playMode == PlayMode_InMobilePreview || playMode == PlayMode_InVulkanPreview ||
             playMode == PlayMode_InNewProcess) {
    GUnrealEd->RequestPlaySession(StartLocation, StartRotation,
                                  playMode == PlayMode_InMobilePreview,
                                  playMode == PlayMode_InVulkanPreview,
                                  TEXT(""));
  } else if (playMode == PlayMode_InViewPort) {
    GUnrealEd->RequestPlaySession(atPlayerStart, ActiveLevelViewport, false,
                                  StartLocation, StartRotation);
  } else {
    // PlayMode_Simulate
    GUnrealEd->RequestPlaySession(false, ActiveLevelViewport, true);
  }
}

static int ModeFromSettings() {
    ULevelEditorPlaySettings *PlayInSettings =
      GetMutableDefault<ULevelEditorPlaySettings>();
  if (!PlayInSettings)
    return 0;
  
  int32 numberOfClients;
  bool netDedicated;
  PlayInSettings->GetPlayNumberOfClients(numberOfClients);
  PlayInSettings->GetPlayNetDedicated(netDedicated);
  bool atPlayerStart = PlayInSettings->LastExecutedPlayModeLocation == PlayLocation_DefaultPlayerStart;
  return (numberOfClients - 1) + (atPlayerStart ? (1 << 2) : 0) + (netDedicated ? (1 << 3) : 0) +
    (PlayModeToInt(PlayInSettings->LastExecutedPlayModeType) << 4);
}

void FRiderLinkModule::StartupModule() {
  using namespace Jetbrains::EditorPlugin;

  static const auto START_TIME = FDateTime::UtcNow();

  static const auto GetTimeNow = [](double Time) -> rd::DateTime {
    return rd::DateTime(static_cast<std::time_t>(START_TIME.ToUnixTimestamp() +
                                                 static_cast<int64>(Time)));
  };

  rdConnection.init();
  if(FSlateApplication::IsInitialized())
  {
    slateApplication = &FSlateApplication::Get();
  }
  

  UE_LOG(FLogRiderLinkModule, Log, TEXT("INIT START"));
  rdConnection.scheduler.queue([this] {
    rdConnection.unrealToBackendModel.get_play().advise(
        rdConnection.lifetime, [this](int playValue) {
          if (PlayFromUnreal)
            return;
          SetForTheScope s(PlayFromRider);

          if (!playValue && GUnrealEd && GUnrealEd->PlayWorld) {
            GUnrealEd->RequestEndPlayMap();
          } else if (playValue == 1 && GUnrealEd) {
            if (GUnrealEd->PlayWorld &&
                GUnrealEd->PlayWorld->bDebugPauseExecution) {
              GUnrealEd->PlayWorld->bDebugPauseExecution = false;
            } else {
              const int mode = rdConnection.unrealToBackendModel.get_playMode().get();
              RequestPlay(mode);
            }
          } else if (playValue == 2 && GUnrealEd && GUnrealEd->PlayWorld) {
            GUnrealEd->PlayWorld->bDebugPauseExecution = true;
          }
        });
    rdConnection.unrealToBackendModel.get_frameSkip().advise(
        rdConnection.lifetime, [this](bool skip) {
          GUnrealEd->PlayWorld->bDebugFrameStepExecution = true;
          GUnrealEd->PlayWorld->bDebugPauseExecution = false;
    });
  });

  FEditorDelegates::BeginPIE.AddLambda([this](const bool started) {
    rdConnection.scheduler.queue([this]() {
      if (GUnrealEd && !PlayFromRider) {
        SetForTheScope s(PlayFromUnreal);
        rdConnection.unrealToBackendModel.get_playMode().set(ModeFromSettings());
        rdConnection.unrealToBackendModel.get_play().set(true);
      }
    });
  });

  FEditorDelegates::EndPIE.AddLambda([this](const bool started) {
    rdConnection.scheduler.queue([this]() {
      if (GUnrealEd && !PlayFromRider) {
        SetForTheScope s(PlayFromUnreal);
        rdConnection.unrealToBackendModel.get_play().set(false);
      }
    });
  });

  FEditorDelegates::PausePIE.AddLambda([this](const bool paused) {
    rdConnection.scheduler.queue([this]() {
      if (GUnrealEd && !PlayFromRider) {
        SetForTheScope s(PlayFromUnreal);
        rdConnection.unrealToBackendModel.get_play().set(2);
      }
    });
  });

  FEditorDelegates::ResumePIE.AddLambda([this](const bool resumed) {
    rdConnection.scheduler.queue([this]() {
      if (GUnrealEd && !PlayFromRider) {
        SetForTheScope s(PlayFromUnreal);
        rdConnection.unrealToBackendModel.get_play().set(1);
      }
    });
  });

  // Initial sync.
  rdConnection.scheduler.queue([this]() {
    rdConnection.unrealToBackendModel.get_playMode().set(ModeFromSettings());
  });

  static auto MessageEndpoint =
      FMessageEndpoint::Builder("FAssetEditorManager").Build();
  outputDevice.onSerializeMessage.BindLambda(
      [this](const TCHAR *msg, ELogVerbosity::Type Type,
             const class FName &Name, TOptional<double> Time) {
        if (Type > ELogVerbosity::All) return;
        
        rdConnection.scheduler.queue([this, tail = FString(msg), Type,
                                      Name = Name.GetPlainNameString(),
                                      Time]() mutable {
          rd::optional<rd::DateTime> DateTime;
          if (Time) {
            DateTime = GetTimeNow(Time.GetValue());
          }
          LogMessageInfo MessageInfo = LogMessageInfo(Type, Name, DateTime);

          FString toSend;
          while(tail.Split("\n", &toSend, &tail))
          {
            toSend.TrimEndInline();
            rdConnection.unrealToBackendModel.get_unrealLog().fire(
            UnrealLogEvent{std::move(MessageInfo), std::move(toSend)});
          }
          tail.TrimEndInline();
          rdConnection.unrealToBackendModel.get_unrealLog().fire(
              UnrealLogEvent{std::move(MessageInfo), std::move(tail)});
          });
      });

  UE_LOG(FLogRiderLinkModule, Log, TEXT("INIT FINISH"));
  // FDebug::DumpStackTraceToLog();
}

bool FRiderLinkModule::SupportsDynamicReloading() { return true; }

#undef LOCTEXT_NAMESPACE
