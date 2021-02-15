#include "RiderGameControlExtension.hpp"

#include "RiderLink.hpp"

#include "Model/Library/UE4Library/PlayState.Generated.h"

#include "IHeadMountedDisplay.h"
#include "IXRTrackingSystem.h"
#include "LevelEditor.h"
#include "Async/Async.h"
#include "Misc/FeedbackContext.h"
#include "Modules/ModuleManager.h"
#include "Settings/LevelEditorPlaySettings.h"
#include "Framework/Application/SlateApplication.h"
#include "Editor/UnrealEdEngine.h"
#include "HotReload/Public/IHotReload.h"
#include "UnrealEd/Public/Editor.h"

#include "Runtime/Launch/Resources/Version.h"
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
#include "ILevelViewport.h"
#include "LevelEditorViewport.h"
#else
#include "IAssetViewport.h"
#include "EditorViewportClient.h"
#endif

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderGameControlExtensionModule);

IMPLEMENT_MODULE(FRiderGameControlExtensionModule, RiderGameControlExtension);

extern UNREALED_API class UUnrealEdEngine* GUnrealEd;

static int NumberOfPlayers(int Mode) { return (Mode & 3) + 1; }

static bool SpawnAtPlayerStart(int Mode) { return (Mode & 4) != 0; }

static bool DedicatedServer(int Mode) { return (Mode & 8) != 0; }

enum class Compile
{
    Yes,
    No
};

static Compile CompileBeforeRun(int Mode) { return (Mode & 128) != 0 ? Compile::Yes : Compile::No; }

static EPlayModeType PlayModeFromInt(int ModeNumber)
{
    switch (ModeNumber)
    {
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

static int PlayModeToInt(EPlayModeType modeType)
{
    switch (modeType)
    {
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

static void CompileIfHotReloadEnabled()
{
    check(IsInGameThread());
    if (!IHotReloadModule::IsAvailable())
        return;

    IHotReloadModule& HotReloadModule = IHotReloadModule::Get();
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
    HotReloadModule.RecompileModule(FApp::GetProjectName(), true, *GWarn);
#else
    HotReloadModule.RecompileModule(FApp::GetProjectName(), *GWarn,
                                    ERecompileModuleFlags::ReloadAfterRecompile |
                                    ERecompileModuleFlags::FailIfGeneratedCodeChanges);
#endif
}

// This template has purpose.
// Before UE 4.24, RequestPlaySession was taking TSharedPtr<ILevelViewport>
// On UE 4.24, RequestPlaySession takes TSharedPtr<IAssetViewport>
// Starting from UE 4.25, RequestPlaySession takes FRequestPlaySessionParams and other overrides become obsolete
template <typename T>
static void RequestPlaySession(bool bAtPlayerStart, TSharedPtr<T> DestinationViewport, bool bInSimulateInEditor,
                               const FVector* StartLocation, const FRotator* StartRotation,
                               int32 DestinationConsole, bool bUseMobilePreview, bool bUseVRPreview,
                               bool bUseVulkanPreview, Compile NeedCompile)
{
    AsyncTask(ENamedThreads::GameThread, [=]()
    {
        if (NeedCompile == Compile::Yes)
            CompileIfHotReloadEnabled();
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 24
        GUnrealEd->RequestPlaySession(bAtPlayerStart, DestinationViewport, bInSimulateInEditor, StartLocation,
                                      StartRotation, DestinationConsole, bUseMobilePreview, bUseVRPreview,
                                      bUseVulkanPreview);
#else
        FRequestPlaySessionParams PlaySessionParams;

        if (StartLocation)
        {
            PlaySessionParams.StartLocation = *StartLocation;
            PlaySessionParams.StartRotation = StartRotation ? *StartRotation : FRotator::ZeroRotator;
        }
        if (DestinationViewport != nullptr)
        {
            PlaySessionParams.DestinationSlateViewport = DestinationViewport;
        }

        if (bInSimulateInEditor)
        {
            PlaySessionParams.WorldType = EPlaySessionWorldType::SimulateInEditor;
        }

        if (bUseVRPreview)
        {
            check(!bUseMobilePreview && !bUseVulkanPreview);
            PlaySessionParams.SessionPreviewTypeOverride = EPlaySessionPreviewType::VRPreview;
        }

        if (bUseVulkanPreview)
        {
            check(!bUseMobilePreview && !bUseVRPreview);
            PlaySessionParams.SessionPreviewTypeOverride = EPlaySessionPreviewType::VulkanPreview;
            PlaySessionParams.SessionDestination = EPlaySessionDestinationType::NewProcess;
        }

        if (bUseMobilePreview)
        {
            check(!bUseVRPreview && !bUseVulkanPreview);
            PlaySessionParams.SessionPreviewTypeOverride = EPlaySessionPreviewType::MobilePreview;
            PlaySessionParams.SessionDestination = EPlaySessionDestinationType::NewProcess;
        }
        GUnrealEd->RequestPlaySession(PlaySessionParams);
#endif
    });
}

void RequestPlaySession(const FVector* StartLocation, const FRotator* StartRotation, bool MobilePreview,
                        bool VulkanPreview, const FString& MobilePreviewTargetDevice, Compile NeedCompile,
                        FString AdditionalStandaloneLaunchParameters = TEXT(""))
{
    AsyncTask(ENamedThreads::GameThread, [=]()
    {
        if (NeedCompile == Compile::Yes)
            CompileIfHotReloadEnabled();
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 24
        GUnrealEd->RequestPlaySession(StartLocation, StartRotation, MobilePreview, VulkanPreview,
                                      MobilePreviewTargetDevice, AdditionalStandaloneLaunchParameters);
#else
        FRequestPlaySessionParams PlaySessionParams;

        if (MobilePreview)
        {
            check(!VulkanPreview);
            PlaySessionParams.SessionDestination = EPlaySessionDestinationType::NewProcess;
            PlaySessionParams.SessionPreviewTypeOverride = EPlaySessionPreviewType::MobilePreview;
            PlaySessionParams.MobilePreviewTargetDevice = MobilePreviewTargetDevice;
            PlaySessionParams.AdditionalStandaloneCommandLineParameters = AdditionalStandaloneLaunchParameters;
        }

        if (VulkanPreview)
        {
            check(!MobilePreview);
            PlaySessionParams.SessionDestination = EPlaySessionDestinationType::NewProcess;
            PlaySessionParams.SessionPreviewTypeOverride = EPlaySessionPreviewType::VulkanPreview;
            PlaySessionParams.AdditionalStandaloneCommandLineParameters = AdditionalStandaloneLaunchParameters;
        }

        if (StartLocation)
        {
            PlaySessionParams.StartLocation = *StartLocation;
            PlaySessionParams.StartRotation = StartRotation ? *StartRotation : FRotator::ZeroRotator;
        }
        GUnrealEd->RequestPlaySession(PlaySessionParams);
#endif
    });
}

FSlateApplication* SlateApplication = nullptr;

struct FPlaySettings
{
    EPlayModeType PlayMode;
    int32 NumberOfClients;
    bool bNetDedicated;
    bool bSpawnAtPlayerStart;

    static FPlaySettings UnpackFromMode(int32_t mode)
    {
        FPlaySettings settings = {
            PlayModeFromInt((mode & (16 + 32 + 64)) >> 4),
            NumberOfPlayers(mode),
            DedicatedServer(mode),
            SpawnAtPlayerStart(mode),
        };
        return settings;
    }

    static int32_t PackToMode(const FPlaySettings& settings)
    {
        return (settings.NumberOfClients - 1) +
            (settings.bSpawnAtPlayerStart ? (1 << 2) : 0) +
            (settings.bNetDedicated ? (1 << 3) : 0) +
            (PlayModeToInt(settings.PlayMode) << 4);
    }
};


static FPlaySettings RetrieveSettings(ULevelEditorPlaySettings* PlayInSettings)
{
    check(PlayInSettings);

    FPlaySettings settings;
    settings.PlayMode = PlayInSettings->LastExecutedPlayModeType;
    PlayInSettings->GetPlayNumberOfClients(settings.NumberOfClients);
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 24
    PlayInSettings->GetPlayNetDedicated(settings.bNetDedicated);
#else
    settings.bNetDedicated = PlayInSettings->bLaunchSeparateServer;
#endif
    settings.bSpawnAtPlayerStart = PlayInSettings->LastExecutedPlayModeLocation == PlayLocation_DefaultPlayerStart;

    return settings;
}

static void UpdateSettings(ULevelEditorPlaySettings* PlayInSettings, const FPlaySettings& settings)
{
    check(PlayInSettings);
    
    PlayInSettings->SetPlayNumberOfClients(settings.NumberOfClients);
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 24
    PlayInSettings->SetPlayNetDedicated(settings.bNetDedicated);
#else
    PlayInSettings->bLaunchSeparateServer = settings.bNetDedicated;
#endif
    PlayInSettings->LastExecutedPlayModeLocation =
        settings.bSpawnAtPlayerStart
            ? PlayLocation_DefaultPlayerStart
            : PlayLocation_CurrentCameraLocation;
    PlayInSettings->LastExecutedPlayModeType = settings.PlayMode;

    PlayInSettings->PostEditChange();
    PlayInSettings->SaveConfig();
}

static void RequestPlay(int32_t mode)
{
    FLevelEditorModule& LevelEditorModule =
        FModuleManager::GetModuleChecked<FLevelEditorModule>(
            TEXT("LevelEditor"));
    auto ActiveLevelViewport = LevelEditorModule.GetFirstActiveViewport();
    ULevelEditorPlaySettings* PlayInSettings =
        GetMutableDefault<ULevelEditorPlaySettings>();
    FPlaySettings requestedSettings = FPlaySettings::UnpackFromMode(mode);
    if (PlayInSettings)
    {
        UpdateSettings(PlayInSettings, requestedSettings);
    }
    const EPlayModeType PlayMode = requestedSettings.PlayMode;
    const bool bSpawnAtPlayerStart = requestedSettings.bSpawnAtPlayerStart;
    const FVector* StartLocation = nullptr;
    const FRotator* StartRotation = nullptr;
    if (!bSpawnAtPlayerStart && SlateApplication && ActiveLevelViewport.IsValid() &&
        SlateApplication->FindWidgetWindow(ActiveLevelViewport->AsWidget()).IsValid())
    {
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
        StartLocation = &ActiveLevelViewport->GetLevelViewportClient().GetViewLocation();
        StartRotation = &ActiveLevelViewport->GetLevelViewportClient().GetViewRotation();
#else
        StartLocation = &ActiveLevelViewport->GetAssetViewportClient().GetViewLocation();
        StartRotation = &ActiveLevelViewport->GetAssetViewportClient().GetViewRotation();
#endif
    }

    auto Compile = CompileBeforeRun(mode);

    if (PlayMode == PlayMode_InEditorFloating)
    {
        RequestPlaySession(bSpawnAtPlayerStart, ActiveLevelViewport, false, StartLocation, nullptr, -1, false, false,
                           false, Compile);
    }
    else if (PlayMode == PlayMode_InVR)
    {
        const bool bHMDIsReady = GEngine &&
                                 GEngine->XRSystem.IsValid() &&
                                 GEngine->XRSystem->GetHMDDevice() &&
                                 GEngine->XRSystem->GetHMDDevice()->IsHMDConnected();

        RequestPlaySession(bSpawnAtPlayerStart, ActiveLevelViewport, false, StartLocation, StartRotation, -1, false,
                           bHMDIsReady, false, Compile);
    }
    else if (PlayMode == PlayMode_InMobilePreview ||
            PlayMode == PlayMode_InVulkanPreview ||
            PlayMode == PlayMode_InNewProcess)
    {
        RequestPlaySession(StartLocation, StartRotation, PlayMode == PlayMode_InMobilePreview,
                           PlayMode == PlayMode_InVulkanPreview, TEXT(""), Compile);
    }
    else if (PlayMode == PlayMode_InViewPort)
    {
        RequestPlaySession(bSpawnAtPlayerStart, ActiveLevelViewport, false, StartLocation, StartRotation, -1, false,
                           false, false, Compile);
    }
    else
    {
        // PlayMode_Simulate
        RequestPlaySession(false, ActiveLevelViewport, true, nullptr, nullptr, -1, false, false, false, Compile);
    }
}

void FRiderGameControlExtensionModule::StartupModule()
{
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("STARTUP START"));

    if (FSlateApplication::IsInitialized())
    {
        SlateApplication = &FSlateApplication::Get();
    }

    FRiderLinkModule& RiderLinkModule = FRiderLinkModule::Get();
    RdConnection& RdConnection = RiderLinkModule.RdConnection;
    JetBrains::EditorPlugin::RdEditorModel& UnrealToBackendModel = RdConnection.UnrealToBackendModel;

    const rd::Lifetime NestedLifetime = RiderLinkModule.CreateNestedLifetime();
    RdConnection.Scheduler.queue([NestedLifetime, &UnrealToBackendModel, this]()
    {
        UnrealToBackendModel.get_playStateFromRider().advise(
            NestedLifetime,
            [&UnrealToBackendModel, this](JetBrains::EditorPlugin::PlayState State)
            {
                if (!GUnrealEd) return;

                switch (State)
                {
                case JetBrains::EditorPlugin::PlayState::Idle:
                    if (GUnrealEd->PlayWorld)
                    {
                        GUnrealEd->RequestEndPlayMap();
                    }
                    break;
                case JetBrains::EditorPlugin::PlayState::Play:
                    if (GUnrealEd->PlayWorld &&
                        GUnrealEd->PlayWorld->IsPaused())
                    {
                        GUnrealEd->PlayWorld->bDebugPauseExecution = false;
                        // Simply switching flag doesn't work, `ResumePIE` delegate won't be triggered
                        GUnrealEd->PlaySessionResumed();
                    }
                    else
                    {
                        RequestPlay(playMode);
                    }
                    break;
                case JetBrains::EditorPlugin::PlayState::Pause:
                    if (GUnrealEd->PlayWorld)
                    {
                        GUnrealEd->PlayWorld->bDebugPauseExecution = true;
                        // Simply switching flag doesn't work, `PausePIE` delegate won't be triggered
                        GUnrealEd->PlaySessionPaused();
                    }
                    break;
                }
            });
        UnrealToBackendModel.get_playModeFromRider().advise(
            NestedLifetime,
            [&UnrealToBackendModel, this](int32_t mode)
        {
            ULevelEditorPlaySettings* PlayInSettings = GetMutableDefault<ULevelEditorPlaySettings>();
            if (PlayInSettings)
            {
                const FPlaySettings NewSettings = FPlaySettings::UnpackFromMode(mode);
                UpdateSettings(PlayInSettings, NewSettings);
            }
        });
    });

    RdConnection.Scheduler.queue([NestedLifetime, &UnrealToBackendModel]()
    {
        UnrealToBackendModel.get_frameSkip().advise(NestedLifetime, []()
        {
            if (!GUnrealEd) return;

            GUnrealEd->PlayWorld->bDebugFrameStepExecution = true;
            GUnrealEd->PlayWorld->bDebugPauseExecution = false;
            GUnrealEd->PlaySessionSingleStepped();
        });
    });

    FEditorDelegates::BeginPIE.AddLambda([this, &RdConnection](const bool)
    {        
        ULevelEditorPlaySettings* PlayInSettings = GetMutableDefault<ULevelEditorPlaySettings>();
        if (PlayInSettings)
        {
            FPlaySettings Settings = RetrieveSettings(PlayInSettings);
            playMode = FPlaySettings::PackToMode(Settings);
        }
        RdConnection.Scheduler.queue([&RdConnection, _playMode = playMode]()
        {
            if (!GUnrealEd) return;

            RdConnection.UnrealToBackendModel.get_playModeFromEditor().fire(_playMode);
            RdConnection.UnrealToBackendModel.get_playStateFromEditor().fire(JetBrains::EditorPlugin::PlayState::Play);
        });
    });

    FEditorDelegates::EndPIE.AddLambda([this, &RdConnection](const bool)
    {
        RdConnection.Scheduler.queue([&RdConnection]()
        {
            if (!GUnrealEd) return;

            RdConnection.UnrealToBackendModel.get_playStateFromEditor().fire(JetBrains::EditorPlugin::PlayState::Idle);
        });
    });

    FEditorDelegates::PausePIE.AddLambda([this, &RdConnection](const bool)
    {
        RdConnection.Scheduler.queue([&RdConnection]()
        {
            if (!GUnrealEd) return;

            RdConnection.UnrealToBackendModel.get_playStateFromEditor().fire(JetBrains::EditorPlugin::PlayState::Pause);
        });
    });

    FEditorDelegates::ResumePIE.AddLambda([this, &RdConnection](const bool)
    {
        RdConnection.Scheduler.queue([&RdConnection]()
        {
            if (!GUnrealEd) return;

            RdConnection.UnrealToBackendModel.get_playStateFromEditor().fire(JetBrains::EditorPlugin::PlayState::Play);
        });
    });

    FEditorDelegates::SingleStepPIE.AddLambda([this, &RdConnection](const bool)
    {
        RdConnection.Scheduler.queue([&RdConnection]()
        {
            if (!GUnrealEd) return;

            RdConnection.UnrealToBackendModel.get_playStateFromEditor().fire(JetBrains::EditorPlugin::PlayState::Play);
            RdConnection.UnrealToBackendModel.get_playStateFromEditor().fire(JetBrains::EditorPlugin::PlayState::Pause);
        });
    });
    
    FCoreUObjectDelegates::OnObjectPropertyChanged.AddLambda(
        [this, &RdConnection](UObject* obj, FPropertyChangedEvent& ev)
        {
            ULevelEditorPlaySettings* PlayInSettings = GetMutableDefault<ULevelEditorPlaySettings>();
            if (!PlayInSettings || obj != PlayInSettings) return;
                
            const FPlaySettings Settings = RetrieveSettings(PlayInSettings);
            int PlayModeNew = FPlaySettings::PackToMode(Settings);
            if (PlayModeNew == playMode) return;

            playMode = PlayModeNew;
            RdConnection.Scheduler.queue([&RdConnection, PlayModeNew]()
            {
                if (!GUnrealEd) return;

                RdConnection.UnrealToBackendModel.get_playModeFromEditor().fire(PlayModeNew);
            });
        });

    // Initial sync.
    ULevelEditorPlaySettings* PlayInSettings = GetMutableDefault<ULevelEditorPlaySettings>();
    if (PlayInSettings)
    {
        const FPlaySettings Settings = RetrieveSettings(PlayInSettings);
        playMode = FPlaySettings::PackToMode(Settings);
    }
    RdConnection.Scheduler.queue([&RdConnection, lambdaPlayMode=playMode]()
    {        
        RdConnection.UnrealToBackendModel.get_playModeFromEditor().fire(lambdaPlayMode);
    });
    RdConnection.Scheduler.queue([this, NestedLifetime, &RdConnection]()
    {
       RdConnection.UnrealToBackendModel.get_playModeFromRider().advise(NestedLifetime, [this](int32_t inPlayMode)
       {
           playMode = inPlayMode;
       }); 
    });
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("STARTUP FINISH"));
}

void FRiderGameControlExtensionModule::ShutdownModule()
{
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("SHUTDOWN START"));

    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("SHUTDOWN FINISH"));
}
