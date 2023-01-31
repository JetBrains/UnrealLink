// Fill out your copyright notice in the Description page of Project Settings.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "MyActorPlugin.generated.h"

UCLASS()
class TESTPLUGIN_API AMyActorPlugin : public AActor
{
	GENERATED_BODY()

public:
	// Sets default values for this actor's properties
	AMyActorPlugin();

protected:
	// Called when the game starts or when spawned
	virtual void BeginPlay() override;

public:
	// Called every frame
	virtual void Tick(float DeltaTime) override;

	UPROPERTY(EditAnywhere)
	uint8 bMyPropertyPlugin:1;
};

USTRUCT()
struct FMyStructPlugin
{
	GENERATED_BODY()
};

UENUM()
enum EMyEnumPlugin { Some_Field };


UENUM()
enum class EMyEnumClassPlugin : uint8 { Some_Field };