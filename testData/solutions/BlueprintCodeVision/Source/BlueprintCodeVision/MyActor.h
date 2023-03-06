// Fill out your copyright notice in the Description page of Project Settings.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "MyActor.generated.h"

UCLASS()
class BLUEPRINTCODEVISION_API AMyActor_RENAME : public AActor
{
	GENERATED_BODY()

public:
	// Sets default values for this actor's properties
	AMyActor_RENAME();

protected:
	// Called when the game starts or when spawned
	virtual void BeginPlay() override;

public:
	// Called every frame
	virtual void Tick(float DeltaTime) override;

	// changed in method in MyActorBlueprint 
	UPROPERTY(BlueprintReadWrite)
	FVector MyVector_RENAME;

	// changed in MyActorBlueprint, MyActorBlueprint_Child
	UPROPERTY(EditAnywhere)
	FVector MyEditAnywhereVector = FVector::ZeroVector;

	// implemented in MyActorBlueprint_Child
	UFUNCTION(BlueprintImplementableEvent)
	void MyBlueprintImplementableEvent_Implemented();

	UFUNCTION(BlueprintImplementableEvent)
	void MyBlueprintImplementableEvent_NotImplemented();

	// implemented in MyActorBlueprint_Child
	UFUNCTION(BlueprintNativeEvent)
	void MyBlueprintCallableFunction_InheritedInBlueprint();

	// used in MyActorBlueprint, MyActorBlueprint_Child, MyAnimBlueprint
	UFUNCTION(BlueprintCallable)
	void MyBlueprintCallableFunction_UsedInBlueprint_RENAME();

	UFUNCTION(BlueprintCallable)
	void MyBlueprintCallableFunction_UsedInCpp();

	UFUNCTION(BlueprintCallable) 
	void MyBlueprintCallableFunction_Unused();
};
