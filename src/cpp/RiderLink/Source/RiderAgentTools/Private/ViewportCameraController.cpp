#include "ViewportCameraController.hpp"
#include "RiderAgentTools.hpp"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"

#include "Async/Async.h"
#include "GameFramework/Actor.h"
#include "Kismet/KismetMathLibrary.h"
#include "Math/Rotator.h"
#include "Math/Vector.h"

#if WITH_EDITOR
#include "Editor.h"
#include "Subsystems/EditorActorSubsystem.h"
#include "Subsystems/UnrealEditorSubsystem.h"
#endif

// Don't `using namespace JetBrains::EditorPlugin;` anywhere in this TU — the
// RD-generated `JetBrains::EditorPlugin::UClass` struct (from UE4Library.kt)
// collides with UE's global `::UClass`, and unity builds make any leaked
// using-directive contaminate sibling translation units' include of
// KismetMathLibrary.generated.h. We use a `EP::` alias instead.
namespace EP = JetBrains::EditorPlugin;

namespace
{
    // ── Wire ↔ UE math conversions ──────────────────────────────────────────

    FVector ToUE(EP::Vector3 const& V) { return FVector(V.get_x(), V.get_y(), V.get_z()); }
    FRotator ToUE(EP::Rotator3 const& R) { return FRotator(R.get_pitch(), R.get_yaw(), R.get_roll()); }

    rd::Wrapper<EP::Vector3> FromUE(FVector const& V)
    {
        return rd::Wrapper<EP::Vector3>(EP::Vector3(V.X, V.Y, V.Z));
    }
    rd::Wrapper<EP::Rotator3> FromUE(FRotator const& R)
    {
        return rd::Wrapper<EP::Rotator3>(EP::Rotator3(R.Pitch, R.Yaw, R.Roll));
    }

    // Build response shells without repeating the long ctor list.
    EP::ViewportCameraResponse CameraOk(FVector const& Loc, FRotator const& Rot,
                                  rd::optional<FString> ActorResolved = rd::optional<FString>())
    {
        return EP::ViewportCameraResponse(/*success=*/true, FromUE(Loc), FromUE(Rot),
                                          ActorResolved, FString());
    }

    EP::ViewportCameraResponse CameraFail(FString const& Error)
    {
        return EP::ViewportCameraResponse(/*success=*/false,
                                          FromUE(FVector::ZeroVector),
                                          FromUE(FRotator::ZeroRotator),
                                          rd::optional<FString>(),
                                          Error);
    }

    // ── Editor-subsystem access (game thread only) ──────────────────────────

#if WITH_EDITOR
    UUnrealEditorSubsystem* GetEdSubsystem()
    {
        if (!GEditor) return nullptr;
        return GEditor->GetEditorSubsystem<UUnrealEditorSubsystem>();
    }

    bool GetPose(FVector& OutLoc, FRotator& OutRot)
    {
        UUnrealEditorSubsystem* Sub = GetEdSubsystem();
        if (!Sub) return false;
        return Sub->GetLevelViewportCameraInfo(OutLoc, OutRot);
    }

    void SetPose(FVector const& Loc, FRotator const& Rot)
    {
        if (UUnrealEditorSubsystem* Sub = GetEdSubsystem())
        {
            Sub->SetLevelViewportCameraInfo(Loc, Rot);
        }
    }

    // Match the Python prototype's lookup: prefer Outliner label, fall back to
    // FName. Editor-only — GetActorLabel is gated by WITH_EDITOR.
    AActor* FindActorByLabel(FString const& Name)
    {
        if (Name.IsEmpty() || !GEditor) return nullptr;
        UEditorActorSubsystem* Eas = GEditor->GetEditorSubsystem<UEditorActorSubsystem>();
        if (!Eas) return nullptr;
        TArray<AActor*> All = Eas->GetAllLevelActors();
        for (AActor* Actor : All)
        {
            if (Actor && Actor->GetActorLabel() == Name) return Actor;
        }
        for (AActor* Actor : All)
        {
            if (Actor && Actor->GetName() == Name) return Actor;
        }
        return nullptr;
    }
#endif

    // ── Per-action handlers (game thread) ───────────────────────────────────

    EP::ViewportCameraResponse HandleGet(EP::ViewportCameraRequest const& /*Req*/)
    {
#if !WITH_EDITOR
        return CameraFail(TEXT("Viewport camera control is editor-only"));
#else
        FVector Loc; FRotator Rot;
        if (!GetPose(Loc, Rot)) return CameraFail(TEXT("GetLevelViewportCameraInfo returned false"));
        return CameraOk(Loc, Rot);
#endif
    }

    EP::ViewportCameraResponse HandleSet(EP::ViewportCameraRequest const& Req)
    {
#if !WITH_EDITOR
        return CameraFail(TEXT("Viewport camera control is editor-only"));
#else
        FVector Loc; FRotator Rot;
        if (!GetPose(Loc, Rot)) return CameraFail(TEXT("GetLevelViewportCameraInfo returned false"));
        if (Req.get_location()) Loc = ToUE(*Req.get_location());
        if (Req.get_rotation()) Rot = ToUE(*Req.get_rotation());
        SetPose(Loc, Rot);
        return CameraOk(Loc, Rot);
#endif
    }

    EP::ViewportCameraResponse HandleMove(EP::ViewportCameraRequest const& Req)
    {
#if !WITH_EDITOR
        return CameraFail(TEXT("Viewport camera control is editor-only"));
#else
        FVector Loc; FRotator Rot;
        if (!GetPose(Loc, Rot)) return CameraFail(TEXT("GetLevelViewportCameraInfo returned false"));
        if (auto const& D = Req.get_delta())
        {
            FVector const RawDelta(D->get_x(), D->get_y(), D->get_z());
            if (Req.get_relative())
            {
                // x→forward, y→right, z→up in the camera's local frame.
                FVector const Forward = Rot.Vector();
                FVector const Right   = FRotationMatrix(Rot).GetScaledAxis(EAxis::Y);
                FVector const Up      = FRotationMatrix(Rot).GetScaledAxis(EAxis::Z);
                Loc += Forward * RawDelta.X + Right * RawDelta.Y + Up * RawDelta.Z;
            }
            else
            {
                Loc += RawDelta;
            }
        }
        if (auto const& RD = Req.get_rotationDelta())
        {
            Rot += FRotator(RD->get_pitch(), RD->get_yaw(), RD->get_roll());
        }
        SetPose(Loc, Rot);
        return CameraOk(Loc, Rot);
#endif
    }

    EP::ViewportCameraResponse HandleLookAt(EP::ViewportCameraRequest const& Req)
    {
#if !WITH_EDITOR
        return CameraFail(TEXT("Viewport camera control is editor-only"));
#else
        if (!Req.get_target()) return CameraFail(TEXT("target is required for LookAt"));
        FVector Loc; FRotator Rot;
        if (!GetPose(Loc, Rot)) return CameraFail(TEXT("GetLevelViewportCameraInfo returned false"));
        FVector const Target = ToUE(*Req.get_target());
        FRotator const NewRot = UKismetMathLibrary::FindLookAtRotation(Loc, Target);
        SetPose(Loc, NewRot);
        return CameraOk(Loc, NewRot);
#endif
    }

    EP::ViewportCameraResponse HandleFocus(EP::ViewportCameraRequest const& Req)
    {
#if !WITH_EDITOR
        return CameraFail(TEXT("Viewport camera control is editor-only"));
#else
        if (!Req.get_actorName())
        {
            return CameraFail(TEXT("actorName is required for FocusOnActor"));
        }
        FString const RequestedName = *Req.get_actorName();
        AActor* Actor = FindActorByLabel(RequestedName);
        if (!Actor)
        {
            return CameraFail(FString::Printf(TEXT("Actor not found: %s"), *RequestedName));
        }
        FVector Origin, Extent;
        Actor->GetActorBounds(/*bOnlyCollidingComponents=*/false, Origin, Extent);
        double const Radius = FMath::Max3(Extent.X, Extent.Y, FMath::Max(Extent.Z, 50.0));
        double const MinDistance = Req.get_minDistance() > 0.0 ? Req.get_minDistance() : 200.0;
        double const Distance = FMath::Max(Radius * 3.0, MinDistance);
        // Place the camera behind-and-above the actor (matches Python prototype).
        FVector const NewLoc(Origin.X - Distance, Origin.Y - Distance, Origin.Z + Distance * 0.6);
        FRotator const NewRot = UKismetMathLibrary::FindLookAtRotation(NewLoc, Origin);
        SetPose(NewLoc, NewRot);
        rd::optional<FString> Resolved(Actor->GetActorLabel());
        return CameraOk(NewLoc, NewRot, Resolved);
#endif
    }

    EP::ViewportCameraResponse Dispatch(EP::ViewportCameraRequest const& Req)
    {
        check(IsInGameThread());
        switch (Req.get_action())
        {
            case EP::ViewportCameraAction::Get:           return HandleGet(Req);
            case EP::ViewportCameraAction::Set:           return HandleSet(Req);
            case EP::ViewportCameraAction::Move:          return HandleMove(Req);
            case EP::ViewportCameraAction::LookAt:        return HandleLookAt(Req);
            case EP::ViewportCameraAction::FocusOnActor:  return HandleFocus(Req);
        }
        return CameraFail(TEXT("Unknown ViewportCameraAction"));
    }
}

void ViewportCameraController::BindTo(rd::Lifetime /*ModelLifetime*/,
                                      JetBrains::EditorPlugin::RdEditorModel const& Model)
{
    Model.get_viewportCamera().set(
        [](rd::Lifetime, EP::ViewportCameraRequest const& Request) -> rd::RdTask<EP::ViewportCameraResponse>
        {
            rd::RdTask<EP::ViewportCameraResponse> Task;
            // Copy the whole request — it contains rd::Wrapper fields, but
            // ViewportCameraRequest is fully copy-constructible (see the
            // generated header), and the wrappers' refcounts make crossing
            // the AsyncTask boundary safe.
            EP::ViewportCameraRequest Req = Request;
            AsyncTask(ENamedThreads::GameThread,
                [Req, Task]() mutable
                {
                    Task.set(Dispatch(Req));
                });
            return Task;
        });
}
