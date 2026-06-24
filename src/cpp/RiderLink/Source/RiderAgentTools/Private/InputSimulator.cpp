#include "InputSimulator.hpp"
#include "RiderAgentTools.hpp"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"

#include "Async/Async.h"
#include "Containers/Ticker.h"
#include "Engine/Engine.h"
#include "Engine/EngineTypes.h"
#include "Engine/LocalPlayer.h"
#include "Engine/World.h"
#include "GameFramework/Character.h"
#include "GameFramework/Pawn.h"
#include "GameFramework/PlayerController.h"
#include "Kismet/GameplayStatics.h"
#include "Math/Vector.h"
#include "UObject/SoftObjectPath.h"

// Enhanced Input subsystem + types. These are non-editor (runtime) modules.
// `InputAction.h` provides UInputAction; `EnhancedInputSubsystems.h` provides
// UEnhancedInputLocalPlayerSubsystem; `InputActionValue.h` provides FInputActionValue.
#include "EnhancedInputSubsystems.h"
#include "InputAction.h"
#include "InputActionValue.h"

// Same alias-only pattern as ViewportCameraController.cpp — see the comment
// there for the rationale.
namespace EP = JetBrains::EditorPlugin;

namespace
{
    // ── Module state — only one ticker is allowed to be active at a time.
    //   Per the user's "new arm cancels prior ticker" decision: every arm
    //   first stops the in-flight ticker (if any), then registers its own.
    static FTSTicker::FDelegateHandle GTickerHandle;

    void CancelActiveTicker()
    {
        if (GTickerHandle.IsValid())
        {
            FTSTicker::GetCoreTicker().RemoveTicker(GTickerHandle);
            GTickerHandle.Reset();
        }
    }

    // ── PIE world / pawn / controller acquisition ───────────────────────────

    UWorld* FindPIEWorld()
    {
        if (!GEngine) return nullptr;
        for (const FWorldContext& Ctx : GEngine->GetWorldContexts())
        {
            if (Ctx.WorldType == EWorldType::PIE && Ctx.World())
                return Ctx.World();
        }
        return nullptr;
    }

    APawn* GetPlayerPawn(UWorld* World)
    {
        return World ? UGameplayStatics::GetPlayerPawn(World, 0) : nullptr;
    }

    APlayerController* GetPlayerController(UWorld* World)
    {
        return World ? UGameplayStatics::GetPlayerController(World, 0) : nullptr;
    }

    // ── Response shells ─────────────────────────────────────────────────────

    rd::Wrapper<EP::Vector3> WrapVec(const FVector& V)
    {
        return rd::Wrapper<EP::Vector3>(EP::Vector3(V.X, V.Y, V.Z));
    }

    EP::InputSimulationResponse SimOk(bool bArmed, const FVector& StartLoc, const FVector& StartVel, int32 NActions)
    {
        return EP::InputSimulationResponse(
            /*success=*/true, bArmed, WrapVec(StartLoc), WrapVec(StartVel), NActions, FString());
    }

    EP::InputSimulationResponse SimFail(const FString& Error)
    {
        return EP::InputSimulationResponse(
            /*success=*/false, /*armed=*/false,
            rd::Wrapper<EP::Vector3>(), rd::Wrapper<EP::Vector3>(),
            /*nActions=*/0, Error);
    }

    // ── Direction lookup (move primitives) ──────────────────────────────────

    FVector DirectionVector(APawn* Pawn, const FString& Direction)
    {
        if (!Pawn) return FVector::ForwardVector;
        if (Direction == TEXT("forward")) return Pawn->GetActorForwardVector();
        if (Direction == TEXT("back"))    return -Pawn->GetActorForwardVector();
        if (Direction == TEXT("right"))   return Pawn->GetActorRightVector();
        if (Direction == TEXT("left"))    return -Pawn->GetActorRightVector();
        return Pawn->GetActorForwardVector();
    }

    // ── Actions mode ─────────────────────────────────────────────────────────
    //
    // State machine over the request's `actions` list, driven by FTSTicker.
    // Lambda captures the state by value (shared_ptr-like via TSharedPtr).

    struct ActionsState
    {
        TArray<rd::Wrapper<EP::InputActionEntry>> Actions;
        int32 Index = 0;
        double PhaseStart = 0.0;
        bool DoneJumped = false;
        TWeakObjectPtr<APawn> Pawn;
        TWeakObjectPtr<APlayerController> Controller;
    };

    bool ActionsTick(TSharedRef<ActionsState> State, float DeltaSeconds)
    {
        if (State->Index >= State->Actions.Num())
            return false; // unregister
        APawn* Pawn = State->Pawn.Get();
        APlayerController* PC = State->Controller.Get();
        if (!Pawn || !PC)
            return false; // pawn gone — unregister

        const EP::InputActionEntry& A = *State->Actions[State->Index];
        const FString Type = A.get_type();
        const double Duration = A.get_duration();
        const double Now = FPlatformTime::Seconds();
        const double Elapsed = Now - State->PhaseStart;

        auto Advance = [&]() {
            ++State->Index;
            State->PhaseStart = Now;
            State->DoneJumped = false;
        };

        if (Type == TEXT("move"))
        {
            const FString Dir = A.get_direction().has_value() ? *A.get_direction() : FString(TEXT("forward"));
            Pawn->AddMovementInput(DirectionVector(Pawn, Dir), static_cast<float>(A.get_scale()));
            if (Elapsed >= Duration) Advance();
        }
        else if (Type == TEXT("jump"))
        {
            if (!State->DoneJumped)
            {
                if (ACharacter* Character = Cast<ACharacter>(Pawn))
                    Character->Jump();
                State->DoneJumped = true;
            }
            if (Duration <= 0.0 || Elapsed >= Duration) Advance();
        }
        else if (Type == TEXT("look"))
        {
            if (Duration > 0.0)
            {
                const float Frac = static_cast<float>(FMath::Min(DeltaSeconds / Duration, 1.0));
                if (Frac > 0.0f)
                {
                    PC->AddYawInput(static_cast<float>(A.get_yaw() * Frac));
                    PC->AddPitchInput(static_cast<float>(A.get_pitch() * Frac));
                }
                if (Elapsed >= Duration) Advance();
            }
            else
            {
                PC->AddYawInput(static_cast<float>(A.get_yaw()));
                PC->AddPitchInput(static_cast<float>(A.get_pitch()));
                Advance();
            }
        }
        else if (Type == TEXT("wait"))
        {
            if (Elapsed >= Duration) Advance();
        }
        else
        {
            // Unknown — skip.
            Advance();
        }
        return true;
    }

    EP::InputSimulationResponse HandleActions(const EP::InputSimulationRequest& Req)
    {
        UWorld* World = FindPIEWorld();
        if (!World) return SimFail(TEXT("Not in PIE — start a Play session first."));
        APawn* Pawn = GetPlayerPawn(World);
        APlayerController* PC = GetPlayerController(World);
        if (!Pawn || !PC) return SimFail(TEXT("No player pawn / controller in the active PIE world."));

        if (Req.get_actions().Num() == 0) return SimFail(TEXT("`actions` mode requires at least one entry."));

        CancelActiveTicker();
        TSharedRef<ActionsState> State = MakeShared<ActionsState>();
        State->Actions = Req.get_actions();
        State->Index = 0;
        State->PhaseStart = FPlatformTime::Seconds();
        State->Pawn = Pawn;
        State->Controller = PC;

        GTickerHandle = FTSTicker::GetCoreTicker().AddTicker(
            FTickerDelegate::CreateLambda([State](float Dt) -> bool { return ActionsTick(State, Dt); }),
            0.0f /* fire every frame */);

        return SimOk(/*armed=*/true, Pawn->GetActorLocation(), Pawn->GetVelocity(), State->Actions.Num());
    }

    // ── Primitive mode — single sustained call for `primitiveDuration` ──────

    struct PrimitiveState
    {
        FString Call;
        FString Direction;
        FVector WorldVec = FVector::ZeroVector;
        bool UseWorldVec = false;
        double Scale = 1.0;
        double Value = 0.0;
        double EndTime = 0.0;
        TWeakObjectPtr<APawn> Pawn;
        TWeakObjectPtr<APlayerController> Controller;
    };

    bool ApplyPrimitiveOnce(PrimitiveState& S)
    {
        APawn* Pawn = S.Pawn.Get();
        APlayerController* PC = S.Controller.Get();
        if (!Pawn || !PC) return false;
        if (S.Call == TEXT("jump"))
        {
            if (ACharacter* Character = Cast<ACharacter>(Pawn))
                Character->Jump();
            return false; // one-shot
        }
        if (S.Call == TEXT("add_yaw_input"))   { PC->AddYawInput(static_cast<float>(S.Value));   return true; }
        if (S.Call == TEXT("add_pitch_input")) { PC->AddPitchInput(static_cast<float>(S.Value)); return true; }
        if (S.Call == TEXT("add_movement_input"))
        {
            const FVector Dir = S.UseWorldVec ? S.WorldVec : DirectionVector(Pawn, S.Direction);
            Pawn->AddMovementInput(Dir, static_cast<float>(S.Scale));
            return true;
        }
        return false;
    }

    EP::InputSimulationResponse HandlePrimitive(const EP::InputSimulationRequest& Req)
    {
        UWorld* World = FindPIEWorld();
        if (!World) return SimFail(TEXT("Not in PIE — start a Play session first."));
        APawn* Pawn = GetPlayerPawn(World);
        APlayerController* PC = GetPlayerController(World);
        if (!Pawn || !PC) return SimFail(TEXT("No player pawn / controller in the active PIE world."));
        if (!Req.get_primitiveCall().has_value())
            return SimFail(TEXT("`primitive` mode requires `primitiveCall`."));

        CancelActiveTicker();
        TSharedRef<PrimitiveState> State = MakeShared<PrimitiveState>();
        State->Call = *Req.get_primitiveCall();
        State->Direction = Req.get_primitiveDirection().has_value() ? *Req.get_primitiveDirection() : FString(TEXT("forward"));
        State->UseWorldVec = (State->Direction == TEXT("world_vec") && Req.get_primitiveWorldVec().get());
        if (State->UseWorldVec)
        {
            const EP::Vector3& V = *Req.get_primitiveWorldVec();
            State->WorldVec = FVector(V.get_x(), V.get_y(), V.get_z());
        }
        State->Scale = Req.get_primitiveScale();
        State->Value = Req.get_primitiveValue();
        const double Duration = Req.get_primitiveDuration();
        State->EndTime = FPlatformTime::Seconds() + Duration;
        State->Pawn = Pawn;
        State->Controller = PC;

        const bool bSustains = ApplyPrimitiveOnce(*State);
        if (!bSustains || Duration <= 0.0)
        {
            // jump or zero-duration — one-shot, no ticker.
            return SimOk(/*armed=*/false, Pawn->GetActorLocation(), Pawn->GetVelocity(), 0);
        }

        GTickerHandle = FTSTicker::GetCoreTicker().AddTicker(
            FTickerDelegate::CreateLambda([State](float /*Dt*/) -> bool {
                if (FPlatformTime::Seconds() >= State->EndTime) return false;
                return ApplyPrimitiveOnce(*State);
            }),
            0.0f);

        return SimOk(/*armed=*/true, Pawn->GetActorLocation(), Pawn->GetVelocity(), 0);
    }

    // ── Enhanced Input mode ─────────────────────────────────────────────────

    UEnhancedInputLocalPlayerSubsystem* GetEnhancedInputSubsystem(APlayerController* PC)
    {
        if (!PC) return nullptr;
        ULocalPlayer* LP = PC->GetLocalPlayer();
        if (!LP) return nullptr;
        return LP->GetSubsystem<UEnhancedInputLocalPlayerSubsystem>();
    }

    UInputAction* LoadInputAction(const FString& AssetPath)
    {
        FSoftObjectPath SoftPath(AssetPath);
        if (UObject* Loaded = SoftPath.TryLoad())
            return Cast<UInputAction>(Loaded);
        // BP-asset path pattern: append inner-object suffix when needed.
        const FString WithSuffix = AssetPath + TEXT(".") + FPaths::GetBaseFilename(AssetPath);
        if (UObject* Retry = FSoftObjectPath(WithSuffix).TryLoad())
            return Cast<UInputAction>(Retry);
        return nullptr;
    }

    EP::InputSimulationResponse HandleEnhanced(const EP::InputSimulationRequest& Req)
    {
        UWorld* World = FindPIEWorld();
        if (!World) return SimFail(TEXT("Not in PIE — start a Play session first."));
        APlayerController* PC = GetPlayerController(World);
        if (!PC) return SimFail(TEXT("No player controller in the active PIE world."));
        UEnhancedInputLocalPlayerSubsystem* EIS = GetEnhancedInputSubsystem(PC);
        if (!EIS) return SimFail(TEXT("No EnhancedInputLocalPlayerSubsystem on the local player."));

        if (!Req.get_enhancedAssetPath().has_value())
            return SimFail(TEXT("`enhanced` mode requires `enhancedAssetPath`."));
        const FString AssetPath = *Req.get_enhancedAssetPath();
        UInputAction* IA = LoadInputAction(AssetPath);
        if (!IA) return SimFail(FString::Printf(TEXT("InputAction not found: %s"), *AssetPath));

        APawn* Pawn = GetPlayerPawn(World);
        const FVector StartLoc = Pawn ? Pawn->GetActorLocation() : FVector::ZeroVector;
        const FVector StartVel = Pawn ? Pawn->GetVelocity() : FVector::ZeroVector;

        if (Req.get_enhancedClear())
        {
            EIS->StopContinuousInputInjectionForAction(IA);
            return SimOk(/*armed=*/false, StartLoc, StartVel, 0);
        }

        const FString ValueKind = Req.get_enhancedValueKind().has_value() ? *Req.get_enhancedValueKind() : FString(TEXT("axis2d"));
        FInputActionValue Value;
        if (ValueKind == TEXT("axis2d"))
        {
            Value = FInputActionValue(FVector2D(Req.get_enhancedAxis2dX(), Req.get_enhancedAxis2dY()));
        }
        else if (ValueKind == TEXT("axis1d"))
        {
            Value = FInputActionValue(static_cast<float>(Req.get_enhancedAxis1d()));
        }
        else if (ValueKind == TEXT("bool"))
        {
            Value = FInputActionValue(Req.get_enhancedBool());
        }
        else
        {
            return SimFail(FString::Printf(TEXT("Unknown enhancedValueKind: %s"), *ValueKind));
        }

        EIS->StartContinuousInputInjectionForAction(IA, Value, TArray<UInputModifier*>(), TArray<UInputTrigger*>());
        return SimOk(/*armed=*/true, StartLoc, StartVel, 0);
    }

    // ── Dispatch ────────────────────────────────────────────────────────────

    EP::InputSimulationResponse Dispatch(const EP::InputSimulationRequest& Req)
    {
        check(IsInGameThread());
        const FString Mode = Req.get_mode();
        if (Mode == TEXT("actions"))   return HandleActions(Req);
        if (Mode == TEXT("primitive")) return HandlePrimitive(Req);
        if (Mode == TEXT("enhanced"))  return HandleEnhanced(Req);
        return SimFail(FString::Printf(TEXT("Unknown mode: %s"), *Mode));
    }
}

void InputSimulator::BindTo(rd::Lifetime /*ModelLifetime*/,
                            JetBrains::EditorPlugin::RdEditorModel const& Model)
{
    Model.get_simulateInput().set(
        [](rd::Lifetime, EP::InputSimulationRequest const& Request) -> rd::RdTask<EP::InputSimulationResponse>
        {
            EP::InputSimulationRequest Req = Request;
            // Dispatch synchronously: block this thread until the Game Thread finishes.
            // Returning a completed RdTask means RdEndpoint's task.advise() callback fires
            // immediately (while task is still on the stack), avoiding the UAF in
            // RdEndpoint.h where the lambda captures task by reference ([&task]) but
            // task lives on the stack of on_wire_received().
            auto RunOnGameThread = [&]() -> EP::InputSimulationResponse
            {
                if (IsInGameThread())
                    return Dispatch(Req);
                TPromise<EP::InputSimulationResponse> Promise;
                TFuture<EP::InputSimulationResponse> Future = Promise.GetFuture();
                AsyncTask(ENamedThreads::GameThread, [Req, P = MoveTemp(Promise)]() mutable
                {
                    P.SetValue(Dispatch(Req));
                });
                return Future.Get();
            };
            return rd::RdTask<EP::InputSimulationResponse>::from_result(RunOnGameThread());
        });
}
