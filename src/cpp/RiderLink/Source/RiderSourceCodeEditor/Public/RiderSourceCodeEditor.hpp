#pragma once

#include "lifetime/LifetimeDefinition.h"

#include "Delegates/IDelegateInstance.h"
#include "Logging/LogMacros.h"
#include "Logging/LogVerbosity.h"
#include "Modules/ModuleInterface.h"
#include "Styling/SlateTypes.h"
#include "Templates/SharedPointer.h"
#include "UObject/NameTypes.h"

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderSourceCodeEditorModule, Log, All);

class SNotificationItem;
class SWindow;

// When Rider connects and the editor's Source Code Editor (Editor Preferences > General > Source Code)
// is not a Rider accessor, offer to switch it to Rider.
class FRiderSourceCodeEditorModule : public IModuleInterface
{
public:
    FRiderSourceCodeEditorModule() = default;
    virtual ~FRiderSourceCodeEditorModule() override = default;

    /** IModuleInterface implementation */
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;
    virtual bool SupportsDynamicReloading() override { return true; }

private:
    void OnRiderConnected();
    void OnMainFrameCreationFinished(TSharedPtr<SWindow> InRootWindow, bool bIsRunningStartupDialog);
    void TryShowPrompt();
    void OnSetRiderClicked(FName RiderAccessorName);
    ECheckBoxState GetDontAskAgainCheckBoxState() const;
    void OnDontAskAgainCheckBoxChanged(ECheckBoxState NewState);
    void OnDismissClicked();
    void ClosePrompt();

private:
    rd::LifetimeDefinition ModuleLifetimeDefinition;
    FDelegateHandle MainFrameCreationFinishedHandle;
    TWeakPtr<SNotificationItem> PromptItem;
    bool bPromptShownThisSession = false;
    bool bDontAskAgain = false;
};
