#include "RiderGameControlExtension.hpp"

#include "RiderLink.hpp"

#include "IHeadMountedDisplay.h"
#include "IXRTrackingSystem.h"
#include "LevelEditor.h"
#include "Modules/ModuleManager.h"
#include "Settings/LevelEditorPlaySettings.h"
#include "Framework/Application/SlateApplication.h"
#include "UnrealEd.h"

#include "Runtime/Launch/Resources/Version.h"
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 23
#include "ILevelViewport.h"
#include "LevelEditorViewport.h"
#else
#include "IAssetViewport.h"
#endif

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderGameControlExtensionModule);

IMPLEMENT_MODULE(FRiderGameControlExtensionModule, RiderGameControlExtension);

class FSetForTheScope
{
public:
    explicit FSetForTheScope(bool& bValue) : bProxyValue(bValue) { bProxyValue = true; }
    ~FSetForTheScope() { bProxyValue = false; }

private:
    bool& bProxyValue;
};

static int NumberOfPlayers(int Mode) { return (Mode & 3) + 1; }

static bool SpawnAtPlayerStart(int Mode) { return (Mode & 4) != 0; }

static bool DedicatedServer(int Mode) { return (Mode & 8) != 0; }

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

static void RequestPlaySession( bool bAtPlayerStart, TSharedPtr<class IAssetViewport> DestinationViewport, bool bInSimulateInEditor, const FVector* StartLocation = nullptr, const FRotator* StartRotation = nullptr, int32 DestinationConsole = -1, bool bUseMobilePreview = false, bool bUseVRPreview = false, bool bUseVulkanPreview = false)
{
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 24
    GUnrealEd->RequestPlaySession(bAtPlayerStart, DestinationViewport, bInSimulateInEditor, StartLocation, StartRotation, DestinationConsole, bUseMobilePreview, bUseVRPreview, bUseVulkanPreview);
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
}

void RequestPlaySession(const FVector* StartLocation, const FRotator* StartRotation, bool MobilePreview, bool VulkanPreview, const FString& MobilePreviewTargetDevice, FString AdditionalStandaloneLaunchParameters = TEXT(""))
{
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 24
    GUnrealEd->RequestPlaySession(StartLocation, StartRotation, MobilePreview, VulkanPreview, MobilePreviewTargetDevice, AdditionalStandaloneLaunchParameters);
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
}

FSlateApplication* SlateApplication = nullptr;

static void RequestPlay(int mode)
{
    FLevelEditorModule& LevelEditorModule =
        FModuleManager::GetModuleChecked<FLevelEditorModule>(
            TEXT("LevelEditor"));
    auto ActiveLevelViewport = LevelEditorModule.GetFirstActiveViewport();
    ULevelEditorPlaySettings* PlayInSettings =
        GetMutableDefault<ULevelEditorPlaySettings>();
    const EPlayModeType PlayMode = PlayModeFromInt((mode & (16 + 32 + 64)) >> 4);
    const bool bSpawnAtPlayerStart = SpawnAtPlayerStart(mode);
    if (PlayInSettings)
    {
        PlayInSettings->SetPlayNumberOfClients(NumberOfPlayers(mode));
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 24
        PlayInSettings->SetPlayNetDedicated(DedicatedServer(mode));
#else
        PlayInSettings->bLaunchSeparateServer = DedicatedServer(mode);
#endif
        PlayInSettings->LastExecutedPlayModeLocation =
            bSpawnAtPlayerStart
                ? PlayLocation_DefaultPlayerStart
                : PlayLocation_CurrentCameraLocation;
        PlayInSettings->LastExecutedPlayModeType = PlayMode;
    }

    const FVector* StartLocation = nullptr;
    const FRotator* StartRotation = nullptr;
    if (!bSpawnAtPlayerStart && SlateApplication && ActiveLevelViewport.IsValid() &&
        SlateApplication->FindWidgetWindow(ActiveLevelViewport->AsWidget()).IsValid())
    {
#if ENGINE_MINOR_VERSION < 24
        StartLocation = &ActiveLevelViewport->GetLevelViewportClient().GetViewLocation();
        StartRotation = &ActiveLevelViewport->GetLevelViewportClient().GetViewRotation();
#else
        StartLocation = &ActiveLevelViewport->GetAssetViewportClient().GetViewLocation();
        StartRotation = &ActiveLevelViewport->GetAssetViewportClient().GetViewRotation();
#endif
    }

    if (PlayMode == PlayMode_InEditorFloating)
    {
        RequestPlaySession(bSpawnAtPlayerStart, nullptr, false, StartLocation);
    }
    else if (PlayMode == PlayMode_InVR)
    {
        const bool bHMDIsReady = GEngine &&
                                 GEngine->XRSystem.IsValid() &&
                                 GEngine->XRSystem->GetHMDDevice() &&
                                 GEngine->XRSystem->GetHMDDevice()->IsHMDConnected();

        RequestPlaySession(bSpawnAtPlayerStart,
                           ActiveLevelViewport,
                           false, StartLocation,
                           StartRotation, -1,
                           false,
                           bHMDIsReady);
    }
    else if (PlayMode == PlayMode_InMobilePreview ||
            PlayMode == PlayMode_InVulkanPreview ||
            PlayMode == PlayMode_InNewProcess)
    {
        RequestPlaySession(StartLocation, StartRotation,
                                      PlayMode == PlayMode_InMobilePreview,
                                      PlayMode == PlayMode_InVulkanPreview,
                                      TEXT(""));
    }
    else if (PlayMode == PlayMode_InViewPort)
    {
        RequestPlaySession(bSpawnAtPlayerStart, ActiveLevelViewport, false,
                                      StartLocation, StartRotation);
    }
    else
    {
        // PlayMode_Simulate
        RequestPlaySession(false, ActiveLevelViewport, true);
    }
}

static int ModeFromSettings()
{
    ULevelEditorPlaySettings* PlayInSettings = GetMutableDefault<ULevelEditorPlaySettings>();
    if (!PlayInSettings)
        return 0;

    int32 NumberOfClients;
    bool bNetDedicated;
    PlayInSettings->GetPlayNumberOfClients(NumberOfClients);
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 24
    PlayInSettings->GetPlayNetDedicated(bNetDedicated);
#else
    bNetDedicated = PlayInSettings->bLaunchSeparateServer;
#endif
    const bool bSpawnAtPlayerStart = PlayInSettings->LastExecutedPlayModeLocation == PlayLocation_DefaultPlayerStart;
    return (NumberOfClients - 1) + (bSpawnAtPlayerStart ? (1 << 2) : 0) + (bNetDedicated ? (1 << 3) : 0) +
        (PlayModeToInt(PlayInSettings->LastExecutedPlayModeType) << 4);
}


void FRiderGameControlExtensionModule::StartupModule()
{
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("STARTUP START"));

    if (FSlateApplication::IsInitialized())
    {
        SlateApplication = &FSlateApplication::Get();
    }

    const rd::Lifetime NestedLifetime = FRiderLinkModule::Get().CreateNestedLifetime();
    FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_play().advise(
        NestedLifetime, [this](int playValue)
        {
            if (PlayFromUnreal)
                return;
            FSetForTheScope s(PlayFromRider);

            if (!playValue && GUnrealEd && GUnrealEd->PlayWorld)
            {
                GUnrealEd->RequestEndPlayMap();
            }
            else if (playValue == 1 && GUnrealEd)
            {
                if (GUnrealEd->PlayWorld &&
                    GUnrealEd->PlayWorld->bDebugPauseExecution)
                {
                    GUnrealEd->PlayWorld->bDebugPauseExecution = false;
                }
                else
                {
                    const int Mode = FRiderLinkModule::Get()
                                     .RdConnection.UnrealToBackendModel.get_playMode().get();
                    RequestPlay(Mode);
                }
            }
            else if (playValue == 2 && GUnrealEd && GUnrealEd->PlayWorld)
            {
                GUnrealEd->PlayWorld->bDebugPauseExecution = true;
            }
        });

    FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_play().advise(
        NestedLifetime, [this](int playValue)
        {
            if (PlayFromUnreal)
                return;
            FSetForTheScope s(PlayFromRider);

            if (!playValue && GUnrealEd && GUnrealEd->PlayWorld)
            {
                GUnrealEd->RequestEndPlayMap();
            }
            else if (playValue == 1 && GUnrealEd)
            {
                if (GUnrealEd->PlayWorld &&
                    GUnrealEd->PlayWorld->bDebugPauseExecution)
                {
                    GUnrealEd->PlayWorld->bDebugPauseExecution = false;
                }
                else
                {
                    const int mode = FRiderLinkModule::Get()
                                     .RdConnection.UnrealToBackendModel.get_playMode().get();
                    RequestPlay(mode);
                }
            }
            else if (playValue == 2 && GUnrealEd && GUnrealEd->PlayWorld)
            {
                GUnrealEd->PlayWorld->bDebugPauseExecution = true;
            }
        });
    FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_frameSkip().advise(
        NestedLifetime, [this](bool)
        {
            GUnrealEd->PlayWorld->bDebugFrameStepExecution = true;
            GUnrealEd->PlayWorld->bDebugPauseExecution = false;
        });

    FEditorDelegates::BeginPIE.AddLambda([this](const bool)
    {
        if (GUnrealEd && !PlayFromRider)
        {
            FSetForTheScope s(PlayFromUnreal);
            FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_playMode().set(ModeFromSettings());
            FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_play().set(true);
        }
    });

    FEditorDelegates::EndPIE.AddLambda([this](const bool)
    {
        if (GUnrealEd && !PlayFromRider)
        {
            FSetForTheScope s(PlayFromUnreal);
            FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_play().set(false);
        }
    });

    FEditorDelegates::PausePIE.AddLambda([this](const bool)
    {
        if (GUnrealEd && !PlayFromRider)
        {
            FSetForTheScope s(PlayFromUnreal);
            FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_play().set(2);
        }
    });

    FEditorDelegates::ResumePIE.AddLambda([this](const bool)
    {
        if (GUnrealEd && !PlayFromRider)
        {
            FSetForTheScope s(PlayFromUnreal);
            FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_play().set(1);
        }
    });

    // Initial sync.
    FRiderLinkModule::Get().RdConnection.UnrealToBackendModel.get_playMode().set(ModeFromSettings());
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("STARTUP FINISH"));
}

void FRiderGameControlExtensionModule::ShutdownModule()
{
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("SHUTDOWN START"));

    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("SHUTDOWN FINISH"));
}
