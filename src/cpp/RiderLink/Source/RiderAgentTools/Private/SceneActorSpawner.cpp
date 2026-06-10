#include "SceneActorSpawner.hpp"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"

#include "Async/Async.h"
#include "GameFramework/Actor.h"
#include "Math/Rotator.h"
#include "Math/Vector.h"
#include "UObject/UObjectGlobals.h"

#if WITH_EDITOR
#include "Editor.h"
#include "Engine/Blueprint.h"
#include "Subsystems/EditorActorSubsystem.h"
#endif

// Don't `using namespace JetBrains::EditorPlugin;` anywhere in this TU — the
// RD-generated `JetBrains::EditorPlugin::UClass` struct (from UE4Library.kt)
// collides with UE's global `::UClass`, and unity builds make any leaked
// using-directive contaminate sibling translation units. We use a `EP::`
// alias instead. (Same rule as ViewportCameraController.cpp.)
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

    EP::SpawnActorResponse SpawnOk(FString const& Label, FString const& Name, FVector const& Loc)
    {
        return EP::SpawnActorResponse(/*success=*/true,
                                      rd::optional<FString>(Label),
                                      rd::optional<FString>(Name),
                                      FromUE(Loc),
                                      FString());
    }

    EP::SpawnActorResponse SpawnFail(FString const& Error)
    {
        return EP::SpawnActorResponse(/*success=*/false,
                                      rd::optional<FString>(),
                                      rd::optional<FString>(),
                                      FromUE(FVector::ZeroVector),
                                      Error);
    }

    // ── Spawn handler (game thread, editor-only) ─────────────────────────────

    EP::SpawnActorResponse HandleSpawn(EP::SpawnActorRequest const& Req)
    {
        check(IsInGameThread());
#if !WITH_EDITOR
        return SpawnFail(TEXT("Actor spawning is editor-only"));
#else
        if (!GEditor) return SpawnFail(TEXT("GEditor is unavailable"));

        FString const AssetPath = Req.get_assetPath();
        if (AssetPath.IsEmpty()) return SpawnFail(TEXT("assetPath is empty"));

        // Load the asset by long object path (e.g. "/Engine/BasicShapes/Cube.Cube"
        // or "/Game/Heroes/BP_Hero.BP_Hero").
        UObject* Asset = LoadObject<UObject>(nullptr, *AssetPath);
        if (!Asset)
        {
            Asset = StaticLoadObject(UObject::StaticClass(), nullptr, *AssetPath);
        }
        if (!Asset)
        {
            return SpawnFail(FString::Printf(TEXT("Failed to load asset: %s"), *AssetPath));
        }

        UEditorActorSubsystem* Eas = GEditor->GetEditorSubsystem<UEditorActorSubsystem>();
        if (!Eas) return SpawnFail(TEXT("EditorActorSubsystem unavailable"));

        FVector const Loc = ToUE(Req.get_location());
        FRotator const Rot = ToUE(Req.get_rotation());

        // Pick the spawn path by asset kind: a UClass (e.g. a generated *_C class)
        // or a Blueprint spawns an instance of its actor class; a mesh / template
        // object goes through SpawnActorFromObject. GeneratedClass is a
        // TSubclassOf<UObject>; funnel everything through a plain UClass* so the
        // TSubclassOf<AActor> conversion is well-defined, and gate on IsChildOf.
        AActor* Actor = nullptr;
        UClass* SpawnClass = Cast<UClass>(Asset);
        if (!SpawnClass)
        {
            if (UBlueprint* AsBlueprint = Cast<UBlueprint>(Asset))
            {
                SpawnClass = AsBlueprint->GeneratedClass;
            }
        }
        if (SpawnClass)
        {
            if (!SpawnClass->IsChildOf(AActor::StaticClass()))
            {
                return SpawnFail(FString::Printf(
                    TEXT("Asset class is not an Actor subclass: %s"), *AssetPath));
            }
            Actor = Eas->SpawnActorFromClass(SpawnClass, Loc, Rot);
        }
        else
        {
            Actor = Eas->SpawnActorFromObject(Asset, Loc, Rot);
        }

        if (!Actor)
        {
            return SpawnFail(FString::Printf(
                TEXT("Could not spawn an actor from asset: %s (not a placeable mesh or actor class?)"), *AssetPath));
        }

        // Frontend defaults scale to (1,1,1); guard against an accidental zero
        // scale that would make the actor invisible.
        FVector const Scale = ToUE(Req.get_scale());
        if (!Scale.IsZero())
        {
            Actor->SetActorScale3D(Scale);
        }

        if (auto const& Label = Req.get_label())
        {
            Actor->SetActorLabel(*Label);
        }

        return SpawnOk(Actor->GetActorLabel(), Actor->GetName(), Actor->GetActorLocation());
#endif
    }
}

void SceneActorSpawner::BindTo(rd::Lifetime /*ModelLifetime*/,
                               JetBrains::EditorPlugin::RdEditorModel const& Model)
{
    Model.get_spawnActor().set(
        [](rd::Lifetime, EP::SpawnActorRequest const& Request) -> rd::RdTask<EP::SpawnActorResponse>
        {
            rd::RdTask<EP::SpawnActorResponse> Task;
            // Copy the whole request across the AsyncTask boundary — it holds
            // rd::Wrapper fields but is fully copy-constructible (see generated header).
            EP::SpawnActorRequest Req = Request;
            AsyncTask(ENamedThreads::GameThread,
                [Req, Task]() mutable
                {
                    Task.set(HandleSpawn(Req));
                });
            return Task;
        });
}
