// Fill out your copyright notice in the Description page of Project Settings.


#include "MyActor.h"

// Sets default values
AMyActor_RENAME::AMyActor_RENAME()
{
 	// Set this actor to call Tick() every frame.  You can turn this off to improve performance if you don't need it.
	PrimaryActorTick.bCanEverTick = true;

}

// Called when the game starts or when spawned
void AMyActor_RENAME::BeginPlay()
{
	Super::BeginPlay();
	
}

// Called every frame
void AMyActor_RENAME::Tick(float DeltaTime)
{
	Super::Tick(DeltaTime);
	MyBlueprintCallableFunction_UsedInCpp();
}

void AMyActor_RENAME::MyBlueprintCallableFunction_UsedInBlueprint_RENAME()
{
}

void AMyActor_RENAME::MyBlueprintCallableFunction_UsedInCpp()
{
}

void AMyActor_RENAME::MyBlueprintCallableFunction_Unused()
{
}

void AMyActor_RENAME::MyBlueprintCallableFunction_InheritedInBlueprint_Implementation()
{
}

