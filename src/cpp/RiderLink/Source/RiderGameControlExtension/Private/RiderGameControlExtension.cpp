#include "RiderGameControlExtension.hpp"


#include "DebuggerCommands.h"
#include "RiderLink.hpp"

#include "Model/Library/UE4Library/PlayState.Generated.h"
#include "Model/Library/UE4Library/RequestFailed.Generated.h"
#include "Model/Library/UE4Library/RequestSucceed.Generated.h"

#include "LevelEditor.h"
#include "LevelEditorActions.h"
#include "Async/Async.h"
#include "Misc/FeedbackContext.h"
#include "Modules/ModuleManager.h"
#include "Settings/LevelEditorPlaySettings.h"
#include "Framework/Application/SlateApplication.h"
#include "Editor/UnrealEdEngine.h"
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


static FPlaySettings RetrieveSettings(const ULevelEditorPlaySettings* PlayInSettings)
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


struct FCachedCommandInfo
{
    FName CommandName;
    TSharedPtr<FUICommandInfo> Command;
};

class FRiderGameControlActionsCache
{
public:
    FRiderGameControlActionsCache();
    ~FRiderGameControlActionsCache();

private:
    void UpdatePlayWorldCommandsCache();

public:
    FCachedCommandInfo PlayModeCommands[PlayMode_Count] = {
        {TEXT("PlayInViewport")},
        {TEXT("PlayInEditorFloating")},
        {TEXT("PlayInMobilePreview")},
        {FName()},
        {TEXT("PlayInVulkanPreview")},
        {TEXT("PlayInNewProcess")},
        {TEXT("PlayInVR")},
        {TEXT("Simulate")},
    };
    FCachedCommandInfo ResumePlaySession = {TEXT("ResumePlaySession")};
    FCachedCommandInfo PausePlaySession = {TEXT("PausePlaySession")};
    FCachedCommandInfo StopPlaySession = {TEXT("StopPlaySession")};
    FCachedCommandInfo SingleFrameAdvance = {TEXT("SingleFrameAdvance")};

private:
    FDelegateHandle CommandsChangedHandle;
};

FRiderGameControlActionsCache::FRiderGameControlActionsCache()
{
    const FName PlayWorldContextName = FName("PlayWorld");
    
    TSharedPtr<FBindingContext> PlayWorldContext = FInputBindingManager::Get().GetContextByName(PlayWorldContextName);
    if (PlayWorldContext.IsValid())
    {
        UpdatePlayWorldCommandsCache();
    }

    CommandsChangedHandle = FBindingContext::CommandsChanged.AddLambda(
        [this, PlayWorldContextName](const FBindingContext& Ctx)
        {
            if (Ctx.GetContextName() == PlayWorldContextName)
            {
                UpdatePlayWorldCommandsCache();
            }
        }
    );
}

FRiderGameControlActionsCache::~FRiderGameControlActionsCache()
{
    FBindingContext::CommandsChanged.Remove(CommandsChangedHandle);
}

void FRiderGameControlActionsCache::UpdatePlayWorldCommandsCache()
{
    FInputBindingManager& BindingManager = FInputBindingManager::Get();
    auto CacheCommand = [&] (FCachedCommandInfo &Cmd, const FName &ContextName)
    {
        Cmd.Command = BindingManager.FindCommandInContext(ContextName, Cmd.CommandName);
    };
    
    const FName PlayWorldContextName = FName("PlayWorld");
    for (FCachedCommandInfo& PlayModeCommand : PlayModeCommands)
    {
        if (PlayModeCommands->CommandName.IsNone()) continue;
        CacheCommand(PlayModeCommand, PlayWorldContextName);
    }
    CacheCommand(ResumePlaySession, PlayWorldContextName);
    CacheCommand(PausePlaySession, PlayWorldContextName);
    CacheCommand(StopPlaySession, PlayWorldContextName);
    CacheCommand(SingleFrameAdvance, PlayWorldContextName);
}


class FRiderGameControl
{
public:
    FRiderGameControl(rd::Lifetime ConnectionLifetime, RdConnection& Connection, FRiderGameControlActionsCache& actions);
    ~FRiderGameControl();
private:
    void RequestPlayWorldCommand(const FCachedCommandInfo& CommandInfo, int RequestID);

    void SendRequestSucceed(int requestID);
    void SendRequestFailed(int RequestID, JetBrains::EditorPlugin::NotificationType Type, const FString& message);

    void ScheduleModelAction(std::function<void(JetBrains::EditorPlugin::RdEditorModel&)> action);

private:
    rd::LifetimeDefinition LifetimeDefinition;
    RdConnection& Connection;
    FRiderGameControlActionsCache& Actions;
    
    int32_t playMode;

    FDelegateHandle BeginPIEHandle;
    FDelegateHandle EndPIEHandle;
    FDelegateHandle PausePIEHandle;
    FDelegateHandle ResumePIEHandle;
    FDelegateHandle SingleStepPIEHandle;
    FDelegateHandle OnObjectPropertyChangedHandle;
};


void FRiderGameControl::SendRequestSucceed(int requestID)
{
    using namespace JetBrains::EditorPlugin;
    ScheduleModelAction([=](RdEditorModel &Model)
    {
        Model.get_notificationReplyFromEditor().fire(RequestSucceed(requestID));
    });
}

void FRiderGameControl::SendRequestFailed(int RequestID, JetBrains::EditorPlugin::NotificationType Type, const FString& message)
{
    using namespace JetBrains::EditorPlugin;
    ScheduleModelAction([=](RdEditorModel &Model)
    {
        Model.get_notificationReplyFromEditor().fire(RequestFailed(Type, ToCStr(message), RequestID));
    });
}

void FRiderGameControl::RequestPlayWorldCommand(const FCachedCommandInfo& CommandInfo, int requestID)
{
    using namespace JetBrains::EditorPlugin;
    if (!CommandInfo.Command.IsValid())
    {
        const FString Message = FString::Format(TEXT("Command '{0}' was not executed.\nCommand was not registered in Unreal Engine"),
                                                {CommandInfo.CommandName.ToString()});
        SendRequestFailed(requestID, NotificationType::Error, Message);
        return;
    }
    AsyncTask(ENamedThreads::GameThread, [=]()
    {
        if (FPlayWorldCommands::GlobalPlayWorldActions->TryExecuteAction(CommandInfo.Command.ToSharedRef()))
        {
            SendRequestSucceed(requestID);
        }
        else
        {
            const FString Message = FString::Format(TEXT("Command '{0}' was not executed.\nRejected by Unreal Engine"),
                                                    {CommandInfo.CommandName.ToString()});
            SendRequestFailed(requestID, NotificationType::Message, Message);
        }
    });
}

void FRiderGameControl::ScheduleModelAction(std::function<void(JetBrains::EditorPlugin::RdEditorModel&)> action)
{
    Connection.Scheduler.queue([_action = std::move(action), this]()
    {
        _action(Connection.UnrealToBackendModel);
    });
}

FRiderGameControl::FRiderGameControl(rd::Lifetime ConnectionLifetime, RdConnection& RdConnection,
    FRiderGameControlActionsCache &ActionsCache) :
    LifetimeDefinition(ConnectionLifetime), Connection(RdConnection), Actions(ActionsCache)
{
    using namespace JetBrains::EditorPlugin;
    
    rd::Lifetime Lifetime = LifetimeDefinition.lifetime;
    // Subscribe to Editor events
    Lifetime->bracket(
        [this]()
        {
            BeginPIEHandle = FEditorDelegates::BeginPIE.AddLambda([this](const bool)
            {
                ScheduleModelAction([](RdEditorModel& model)
                {
                    model.get_playStateFromEditor().fire(PlayState::Play);
                });
            });
            EndPIEHandle = FEditorDelegates::EndPIE.AddLambda([this](const bool)
            {
                ScheduleModelAction([](RdEditorModel& model)
                {
                    model.get_playStateFromEditor().fire(PlayState::Idle);
                });
            });
            PausePIEHandle = FEditorDelegates::PausePIE.AddLambda([this](const bool)
            {
                ScheduleModelAction([](RdEditorModel& model)
                {
                    model.get_playStateFromEditor().fire(PlayState::Pause);
                });
            });
            ResumePIEHandle = FEditorDelegates::ResumePIE.AddLambda([this](const bool)
            {
                ScheduleModelAction([](RdEditorModel& model)
                {
                    model.get_playStateFromEditor().fire(PlayState::Play);
                });
            });
            SingleStepPIEHandle = FEditorDelegates::SingleStepPIE.AddLambda([this](const bool)
            {
                ScheduleModelAction([](RdEditorModel& model)
                {
                    model.get_playStateFromEditor().fire(PlayState::Play);
                    model.get_playStateFromEditor().fire(PlayState::Pause);
                });
            });

            OnObjectPropertyChangedHandle = FCoreUObjectDelegates::OnObjectPropertyChanged.AddLambda(
                [this](UObject* obj, FPropertyChangedEvent& ev)
                {
                    ULevelEditorPlaySettings* PlayInSettings = GetMutableDefault<ULevelEditorPlaySettings>();
                    if (!PlayInSettings || obj != PlayInSettings) return;

                    const FPlaySettings Settings = RetrieveSettings(PlayInSettings);
                    int PlayModeNew = FPlaySettings::PackToMode(Settings);
                    if (PlayModeNew == playMode) return;

                    playMode = PlayModeNew;
                    ScheduleModelAction([PlayModeNew](RdEditorModel& Model)
                    {
                        Model.get_playModeFromEditor().fire(PlayModeNew);
                    });
                }
            );
        },
        [this]()
        {
            FCoreUObjectDelegates::OnObjectPropertyChanged.Remove(OnObjectPropertyChangedHandle);
            FEditorDelegates::SingleStepPIE.Remove(SingleStepPIEHandle);
            FEditorDelegates::ResumePIE.Remove(ResumePIEHandle);
            FEditorDelegates::PausePIE.Remove(PausePIEHandle);
            FEditorDelegates::EndPIE.Remove(EndPIEHandle);
            FEditorDelegates::BeginPIE.Remove(BeginPIEHandle);
        }
    );

    // Subscribe to model
    ScheduleModelAction([Lifetime, this](RdEditorModel& Model)
    {
        Model.get_requestPlayFromRider()
             .advise(Lifetime, [this](int requestID)
                     {
                         const ULevelEditorPlaySettings* PlayInSettings
                             = GetDefault<ULevelEditorPlaySettings>();
                         check(PlayInSettings);
                         const EPlayModeType PlayMode = PlayInSettings->LastExecutedPlayModeType;

                         RequestPlayWorldCommand(Actions.PlayModeCommands[PlayMode], requestID);
                     }
             );
        Model.get_requestPauseFromRider()
             .advise(Lifetime, [this](int requestID)
                     {
                         RequestPlayWorldCommand(Actions.PausePlaySession, requestID);
                     }
             );
        Model.get_requestResumeFromRider()
             .advise(Lifetime, [this](int requestID)
                     {
                         RequestPlayWorldCommand(Actions.ResumePlaySession, requestID);
                     }
             );
        Model.get_requestStopFromRider()
             .advise(Lifetime, [this](int requestID)
                     {
                         RequestPlayWorldCommand(Actions.StopPlaySession, requestID);
                     }
             );
        Model.get_requestFrameSkipFromRider()
             .advise(Lifetime, [this](int requestID)
                     {
                         RequestPlayWorldCommand(Actions.SingleFrameAdvance, requestID);
                     }
             );

        Model.get_playModeFromRider()
             .advise(Lifetime, [this](int32_t mode)
                     {
                         ULevelEditorPlaySettings* PlayInSettings
                             = GetMutableDefault<ULevelEditorPlaySettings>();
                         check(PlayInSettings);
                         const FPlaySettings NewSettings = FPlaySettings::UnpackFromMode(mode);
                         UpdateSettings(PlayInSettings, NewSettings);
                     }
             );
    });

    // Initial sync.
    const ULevelEditorPlaySettings* PlayInSettings = GetDefault<ULevelEditorPlaySettings>();
    check(PlayInSettings);
    const FPlaySettings Settings = RetrieveSettings(PlayInSettings);
    playMode = FPlaySettings::PackToMode(Settings);
    
    ScheduleModelAction([lambdaPlayMode=playMode](RdEditorModel &Model)
    {
        Model.get_playModeFromEditor().fire(lambdaPlayMode);
    });

    // After all initialization finished/scheduled - mark that module was initialized
    Lifetime->bracket(
        [this]()
        {
            ScheduleModelAction([](RdEditorModel& Model)
            {
                Model.get_isGameControlModuleInitialized().set(true);
            });
        },
        [this]()
        {
            ScheduleModelAction([](RdEditorModel& Model)
            {
                Model.get_isGameControlModuleInitialized().set(false);
            });
        }
    );
}

FRiderGameControl::~FRiderGameControl()
{
    LifetimeDefinition.terminate();
}


void FRiderGameControlExtensionModule::StartupModule()
{
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("STARTUP START"));

    FRiderLinkModule& RiderLinkModule = FRiderLinkModule::Get();
    ModuleLifetimeDefinition = rd::LifetimeDefinition(RiderLinkModule.CreateNestedLifetime());
    rd::Lifetime ModuleLifetime = ModuleLifetimeDefinition.lifetime;

    ModuleLifetime->bracket(
        [&]()
        {
            // Actions cache is not related to connection and its lifetimes
            ActionsCache = MakeUnique<FRiderGameControlActionsCache>();
        },
        [&]()
        {
            ActionsCache.Reset();
        }
    );

    RdConnection& RdConnection = RiderLinkModule.RdConnection;
    rd::Lifetime ConnectionLifetime = ModuleLifetime.create_nested();
    ConnectionLifetime->bracket(
        [&]()
        {
            GameControl = MakeUnique<FRiderGameControl>(ConnectionLifetime, RdConnection, *ActionsCache);
        },
        [&]()
        {
            GameControl.Reset();
        }
    );
            
    
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("STARTUP FINISH"));
}

void FRiderGameControlExtensionModule::ShutdownModule()
{
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("SHUTDOWN START"));
    ModuleLifetimeDefinition.terminate();
    UE_LOG(FLogRiderGameControlExtensionModule, Verbose, TEXT("SHUTDOWN FINISH"));
}
