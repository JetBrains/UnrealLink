#include "RiderSourceCodeEditor.hpp"
#include "RiderLogMacros.h"

#include "IRiderLink.hpp"

#include "RdEditorModel/RdEditorModel.Pregenerated.h"

#include "Async/Async.h"
#include "Features/IModularFeatures.h"
#include "Framework/Application/SlateApplication.h"
#include "Framework/Notifications/NotificationManager.h"
#include "ISourceCodeAccessModule.h"
#include "ISourceCodeAccessor.h"
#include "Interfaces/IMainFrameModule.h"
#include "Misc/App.h"
#include "Misc/ConfigCacheIni.h"
#include "Modules/ModuleManager.h"
#include "SourceCodeNavigation.h"
#include "Styling/SlateTypes.h"
#include "UObject/Class.h"
#include "UObject/UObjectGlobals.h"
#include "Widgets/Notifications/SNotificationList.h"

#define LOCTEXT_NAMESPACE "RiderSourceCodeEditor"

DEFINE_LOG_CATEGORY(FLogRiderSourceCodeEditorModule);

IMPLEMENT_MODULE(FRiderSourceCodeEditorModule, RiderSourceCodeEditor);

namespace
{
    // USourceCodeAccessSettings is private to SourceCodeAccess. Its class path is also its config section
    // (config=EditorSettings), and PreferredAccessor is the "Source Code Editor" setting.
    const TCHAR* SourceCodeAccessSettingsPath = TEXT("/Script/SourceCodeAccess.SourceCodeAccessSettings");
    const TCHAR* PreferredAccessorKey = TEXT("PreferredAccessor");

    const TCHAR* RiderLinkSection = TEXT("RiderLink");
    const TCHAR* SuppressPromptKey = TEXT("bSuppressSourceCodeEditorPrompt");

    const FName SourceCodeAccessorFeatureName = TEXT("SourceCodeAccessor");

    // Every accessor registered by the engine's RiderSourceCodeAccess plugin is named "Rider ...".
    bool IsRiderAccessor(const ISourceCodeAccessor& Accessor)
    {
        return Accessor.GetFName().ToString().StartsWith(TEXT("Rider"));
    }

    // Prefer the aggregate accessors, which follow the newest Rider install:
    // "Rider" (sln, Windows only), then "Rider Uproject", then any other available Rider accessor.
    ISourceCodeAccessor* FindRiderAccessor()
    {
        static const FName PreferredNames[] = {
            TEXT("Rider"),
            TEXT("Rider Uproject"),
            TEXT("Rider Uproject (experimental)")
        };

        TArray<ISourceCodeAccessor*> RiderAccessors;
        IModularFeatures& ModularFeatures = IModularFeatures::Get();
        const int32 FeatureCount = ModularFeatures.GetModularFeatureImplementationCount(SourceCodeAccessorFeatureName);
        for (int32 FeatureIndex = 0; FeatureIndex < FeatureCount; FeatureIndex++)
        {
            ISourceCodeAccessor* Accessor = static_cast<ISourceCodeAccessor*>(
                ModularFeatures.GetModularFeatureImplementation(SourceCodeAccessorFeatureName, FeatureIndex));
            if (Accessor && IsRiderAccessor(*Accessor) && Accessor->CanAccessSourceCode())
            {
                RiderAccessors.Add(Accessor);
            }
        }

        for (const FName& PreferredName : PreferredNames)
        {
            for (ISourceCodeAccessor* Accessor : RiderAccessors)
            {
                if (Accessor->GetFName() == PreferredName)
                {
                    return Accessor;
                }
            }
        }

        return RiderAccessors.Num() > 0 ? RiderAccessors[0] : nullptr;
    }

    // Not bound to the module object, because the notification keeps this delegate for a few seconds
    // after the prompt closes.
    // Save on toggle, so the choice applies whichever button closes the notification.
    void OnDontAskAgainCheckBoxChanged(ECheckBoxState NewState)
    {
        GConfig->SetBool(RiderLinkSection, SuppressPromptKey, NewState == ECheckBoxState::Checked, GEditorSettingsIni);
        GConfig->Flush(false, GEditorSettingsIni);
    }
}

void FRiderSourceCodeEditorModule::StartupModule()
{
    using namespace JetBrains::EditorPlugin;

    RIDERLINK_LOG(FLogRiderSourceCodeEditorModule, Verbose, "STARTUP START");

    IRiderLinkModule& RiderLinkModule = IRiderLinkModule::Get();
    ModuleLifetimeDefinition = RiderLinkModule.CreateNestedLifetimeDefinition();
    rd::Lifetime ModuleLifetime = ModuleLifetimeDefinition.lifetime;

    RiderLinkModule.ViewModel(
        ModuleLifetime,
        [this](rd::Lifetime ModelLifetime, RdEditorModel const& Model)
        {
            // ViewModel handlers run on the RiderLink scheduler thread; Slate and the accessor need the game thread.
            AsyncTask(ENamedThreads::GameThread, [this, ModelLifetime]()
            {
                // ModelLifetime is nested in ModuleLifetime, which ShutdownModule terminates on the game thread,
                // so this skips tasks queued before ShutdownModule or before Rider disconnected.
                if (ModelLifetime->is_terminated())
                {
                    return;
                }
                OnRiderConnected();
            });
        }
    );

    RIDERLINK_LOG(FLogRiderSourceCodeEditorModule, Verbose, "STARTUP FINISH");
}

void FRiderSourceCodeEditorModule::ShutdownModule()
{
    RIDERLINK_LOG(FLogRiderSourceCodeEditorModule, Verbose, "SHUTDOWN START");
    ModuleLifetimeDefinition.terminate();
    if (MainFrameCreationFinishedHandle.IsValid())
    {
        if (IMainFrameModule* MainFrameModule = FModuleManager::GetModulePtr<IMainFrameModule>(TEXT("MainFrame")))
        {
            MainFrameModule->OnMainFrameCreationFinished().Remove(MainFrameCreationFinishedHandle);
        }
        MainFrameCreationFinishedHandle.Reset();
    }
    // The prompt buttons hold raw delegates to this module.
    ClosePrompt();
    RIDERLINK_LOG(FLogRiderSourceCodeEditorModule, Verbose, "SHUTDOWN FINISH");
}

void FRiderSourceCodeEditorModule::OnRiderConnected()
{
    // Rider often connects while the editor is still starting. A notification raised before the main frame
    // exists is never seen, so wait for the main frame.
    IMainFrameModule& MainFrameModule = FModuleManager::LoadModuleChecked<IMainFrameModule>(TEXT("MainFrame"));
    if (!MainFrameModule.IsWindowInitialized())
    {
        if (!MainFrameCreationFinishedHandle.IsValid())
        {
            MainFrameCreationFinishedHandle = MainFrameModule.OnMainFrameCreationFinished().AddRaw(
                this, &FRiderSourceCodeEditorModule::OnMainFrameCreationFinished);
        }
        return;
    }

    TryShowPrompt();
}

void FRiderSourceCodeEditorModule::OnMainFrameCreationFinished(TSharedPtr<SWindow> InRootWindow, bool bIsRunningStartupDialog)
{
    IMainFrameModule::Get().OnMainFrameCreationFinished().Remove(MainFrameCreationFinishedHandle);
    MainFrameCreationFinishedHandle.Reset();

    TryShowPrompt();
}

void FRiderSourceCodeEditorModule::TryShowPrompt()
{
    // Rider can reconnect several times per editor session; ask once.
    // Unattended sessions have no one to answer a prompt that never expires.
    if (bPromptShownThisSession || FApp::IsUnattended() || GIsRunningUnattendedScript || !FSlateApplication::IsInitialized())
    {
        return;
    }

    // Without a project, the main frame is the Project Browser, and opening a project starts a new session.
    if (!FApp::HasProjectName())
    {
        return;
    }

    bool bSuppressPrompt = false;
    GConfig->GetBool(RiderLinkSection, SuppressPromptKey, bSuppressPrompt, GEditorSettingsIni);
    if (bSuppressPrompt)
    {
        return;
    }

    ISourceCodeAccessModule& SourceCodeAccessModule = FModuleManager::LoadModuleChecked<ISourceCodeAccessModule>(TEXT("SourceCodeAccess"));
    const ISourceCodeAccessor& CurrentAccessor = SourceCodeAccessModule.GetAccessor();
    if (IsRiderAccessor(CurrentAccessor))
    {
        return;
    }

    // Requires the engine's RiderSourceCodeAccess plugin to be enabled.
    const ISourceCodeAccessor* RiderAccessor = FindRiderAccessor();
    if (!RiderAccessor)
    {
        RIDERLINK_LOG(FLogRiderSourceCodeEditorModule, Verbose, "No available Rider source code accessor, skipping prompt");
        return;
    }

    bPromptShownThisSession = true;

    FNotificationInfo Info(FText::Format(
        LOCTEXT("PromptText", "Rider is connected, but the Source Code Editor is {0}."),
        CurrentAccessor.GetNameText()));
    Info.bFireAndForget = false;
    // The text constructor enables the throbber, which reads as a running operation.
    Info.bUseThrobber = false;
    Info.ButtonDetails.Add(FNotificationButtonInfo(
        LOCTEXT("SetRiderButton", "Use Rider"),
        LOCTEXT("SetRiderButtonToolTip", "Set Rider as the Source Code Editor in Editor Preferences."),
        FSimpleDelegate::CreateRaw(this, &FRiderSourceCodeEditorModule::OnSetRiderClicked, RiderAccessor->GetFName())));
    Info.ButtonDetails.Add(FNotificationButtonInfo(
        LOCTEXT("DismissButton", "Dismiss"),
        LOCTEXT("DismissButtonToolTip", "Keep the current Source Code Editor."),
        FSimpleDelegate::CreateRaw(this, &FRiderSourceCodeEditorModule::ClosePrompt)));
    // CheckBoxState keeps its unbound Unchecked default, so the checkbox tracks its own state.
    Info.CheckBoxText = LOCTEXT("DontAskAgainCheckBox", "Don't ask again");
    Info.CheckBoxStateChanged = FOnCheckStateChanged::CreateStatic(&OnDontAskAgainCheckBoxChanged);

    PromptItem = FSlateNotificationManager::Get().AddNotification(Info);
    if (const TSharedPtr<SNotificationItem> Item = PromptItem.Pin())
    {
        // Buttons are only visible in the pending state.
        Item->SetCompletionState(SNotificationItem::CS_Pending);
    }
}

void FRiderSourceCodeEditorModule::OnSetRiderClicked(FName RiderAccessorName)
{
    // Same steps as FSourceCodeNavigation::SetPreferredAccessor, which UE4 does not have.
    GConfig->SetString(SourceCodeAccessSettingsPath, PreferredAccessorKey, *RiderAccessorName.ToString(), GEditorSettingsIni);
    GConfig->Flush(false, GEditorSettingsIni);

    // Keep the settings object in sync with the ini, so Editor Preferences shows the new value
    // and SourceCodeAccess does not fall back to the old value when another accessor registers.
    if (UClass* SettingsClass = FindObject<UClass>(nullptr, SourceCodeAccessSettingsPath))
    {
        SettingsClass->GetDefaultObject()->ReloadConfig();
    }

    ISourceCodeAccessModule& SourceCodeAccessModule = FModuleManager::LoadModuleChecked<ISourceCodeAccessModule>(TEXT("SourceCodeAccess"));
    SourceCodeAccessModule.SetAccessor(RiderAccessorName);
    FSourceCodeNavigation::RefreshCompilerAvailability();

    RIDERLINK_LOG(FLogRiderSourceCodeEditorModule, Log, "Source Code Editor set to '%ls'", *RiderAccessorName.ToString());

    if (const TSharedPtr<SNotificationItem> Item = PromptItem.Pin())
    {
        Item->SetText(FText::Format(
            LOCTEXT("SetRiderDoneText", "The Source Code Editor is now {0}."),
            SourceCodeAccessModule.GetAccessor().GetNameText()));
        Item->SetCompletionState(SNotificationItem::CS_Success);
        Item->ExpireAndFadeout();
    }
    PromptItem.Reset();
}

void FRiderSourceCodeEditorModule::ClosePrompt()
{
    if (const TSharedPtr<SNotificationItem> Item = PromptItem.Pin())
    {
        Item->SetCompletionState(SNotificationItem::CS_None);
        Item->ExpireAndFadeout();
    }
    PromptItem.Reset();
}

#undef LOCTEXT_NAMESPACE
