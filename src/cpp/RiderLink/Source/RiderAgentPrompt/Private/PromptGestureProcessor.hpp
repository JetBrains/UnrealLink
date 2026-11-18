#pragma once

#include "CoreMinimal.h"
#include "Framework/Application/IInputProcessor.h"

/** Fires RdEditorModel::requestPrompt when the user presses Ctrl twice in quick succession in the editor. */
class FPromptGestureProcessor final : public IInputProcessor
{
public:
    /** Call on the game thread. */
    static TSharedPtr<FPromptGestureProcessor> Register();

    /** Call on the game thread. */
    static void Unregister(TSharedPtr<FPromptGestureProcessor>& Processor);

    virtual void Tick(const float DeltaTime, FSlateApplication& SlateApp, TSharedRef<ICursor> Cursor) override {}
    virtual bool HandleKeyDownEvent(FSlateApplication& SlateApp, const FKeyEvent& InKeyEvent) override;
    virtual bool HandleMouseButtonDownEvent(FSlateApplication& SlateApp, const FPointerEvent& MouseEvent) override;
    virtual bool HandleMouseWheelOrGestureEvent(FSlateApplication& SlateApp, const FPointerEvent& InWheelEvent,
                                                const FPointerEvent* InGestureEvent) override;

private:
    double LastCtrlTapTime = 0.0;
};
