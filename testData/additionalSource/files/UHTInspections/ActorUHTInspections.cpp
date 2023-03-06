// Fill out your copyright notice in the Description page of Project Settings.


#include "ActorUHTInspections.h"

// Sets default values
AActorUHTInspections::AActorUHTInspections()
{
 	// Set this actor to call Tick() every frame.  You can turn this off to improve performance if you don't need it.
	PrimaryActorTick.bCanEverTick = true;

}

// Called when the game starts or when spawned
void AActorUHTInspections::BeginPlay()
{
	Super::BeginPlay();
	
}

// Called every frame
void AActorUHTInspections::Tick(float DeltaTime)
{
	Super::Tick(DeltaTime);

}

void AActorUHTInspections::MyFunction_MustBeReliable_Implementation()
{
}

