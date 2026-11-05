#include "PromptGestureProcessor.hpp"

#include "RiderAgentTools.hpp"
#include "RiderLogMacros.h"
#include "IRiderLink.hpp"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"

#include "Framework/Application/SlateApplication.h"
#include "Framework/Notifications/NotificationManager.h"
#include "HAL/PlatformTime.h"
#include "InputCoreTypes.h"
#include "Widgets/Notifications/SNotificationList.h"

#if PLATFORM_WINDOWS
#include "Windows/AllowWindowsPlatformTypes.h"
#include <Windows.h>
#include "Windows/HideWindowsPlatformTypes.h"
#endif

#define LOCTEXT_NAMESPACE "RiderPromptGesture"

namespace
{
    /** The maximum time from the first Ctrl press to the second one. */
    constexpr double DoubleCtrlMaxSeconds = 0.5;

    bool IsBareCtrlPress(const FKeyEvent& KeyEvent)
    {
        const FKey Key = KeyEvent.GetKey();
        return (Key == EKeys::LeftControl || Key == EKeys::RightControl)
            && !KeyEvent.IsShiftDown()
            && !KeyEvent.IsAltDown()
            && !KeyEvent.IsCommandDown();
    }

    void ShowNotConnectedNotification()
    {
#if ENGINE_MAJOR_VERSION >= 5
        FNotificationInfo Info(LOCTEXT("NotConnectedText", "The project is not open in Rider"));
        Info.SubText = LOCTEXT("NotConnectedSubText", "Open the project in Rider to use Ctrl+Ctrl for Air prompt overlay.");
#else
        FNotificationInfo Info(LOCTEXT("NotConnectedText", "The project is not open in Rider\nOpen the project in Rider to use Ctrl+Ctrl for Air prompt overlay."));
#endif
        Info.bFireAndForget = true;
        Info.ExpireDuration = 4.0f;
        Info.bUseThrobber = false;
        FSlateNotificationManager::Get().AddNotification(Info);
    }

    /** Call on the game thread. */
    void RequestPrompt()
    {
        RIDERLINK_LOG(FLogRiderAgentToolsModule, Verbose, "Ctrl+Ctrl: requesting the prompt overlay");
        const bool bQueued = IRiderLinkModule::Get().TryQueueModelAction([](JetBrains::EditorPlugin::RdEditorModel const& Model)
        {
#if PLATFORM_WINDOWS
            rd::IProperty<int64_t> const& RiderProcessId = Model.get_riderProcessId();
            if (RiderProcessId.has_value())
            {
                // The editor holds the foreground after the key press. Rider must get it to focus the prompt.
                ::AllowSetForegroundWindow(static_cast<DWORD>(RiderProcessId.get()));
            }
            else
            {
                RIDERLINK_LOG(FLogRiderAgentToolsModule, Verbose,
                    "Rider process id is not received yet, skipping foreground window permission");
            }
#endif
            Model.get_requestPrompt().fire(JetBrains::EditorPlugin::PromptContext(JetBrains::EditorPlugin::PromptRequestKind::Overlay));
        });

        if (!bQueued)
        {
            RIDERLINK_LOG(FLogRiderAgentToolsModule, Verbose, "Ctrl+Ctrl: Rider is not connected, showing a notification");
            ShowNotConnectedNotification();
        }
    }
}

TSharedPtr<FPromptGestureProcessor> FPromptGestureProcessor::Register()
{
    if (!FSlateApplication::IsInitialized())
    {
        RIDERLINK_LOG(FLogRiderAgentToolsModule, Warning, "Slate is not initialized. The Ctrl+Ctrl prompt gesture is not available.");
        return nullptr;
    }
    TSharedPtr<FPromptGestureProcessor> Processor = MakeShared<FPromptGestureProcessor>();
    FSlateApplication::Get().RegisterInputPreProcessor(Processor);
    return Processor;
}

void FPromptGestureProcessor::Unregister(TSharedPtr<FPromptGestureProcessor>& Processor)
{
    if (Processor.IsValid() && FSlateApplication::IsInitialized())
    {
        FSlateApplication::Get().UnregisterInputPreProcessor(Processor);
    }
    Processor.Reset();
}

bool FPromptGestureProcessor::HandleKeyDownEvent(FSlateApplication&, const FKeyEvent& InKeyEvent)
{
    if (InKeyEvent.IsRepeat())
    {
        return false;
    }

    if (!IsBareCtrlPress(InKeyEvent))
    {
        LastCtrlTapTime = 0.0;
        return false;
    }

    const double Now = FPlatformTime::Seconds();
    if (Now - LastCtrlTapTime <= DoubleCtrlMaxSeconds)
    {
        LastCtrlTapTime = 0.0;
        RequestPrompt();
    }
    else
    {
        LastCtrlTapTime = Now;
    }
    return false;
}

bool FPromptGestureProcessor::HandleMouseButtonDownEvent(FSlateApplication&, const FPointerEvent&)
{
    LastCtrlTapTime = 0.0;
    return false;
}

#undef LOCTEXT_NAMESPACE
