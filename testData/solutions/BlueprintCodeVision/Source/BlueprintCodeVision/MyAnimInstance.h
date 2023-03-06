// Fill out your copyright notice in the Description page of Project Settings.

#pragma once

#include "CoreMinimal.h"
#include "Animation/AnimInstance.h"
#include "MyActor.h"
#include "MyAnimInstance.generated.h"

/**
 * 
 */
UCLASS()
class BLUEPRINTCODEVISION_API UMyAnimInstance : public UAnimInstance
{
	GENERATED_BODY()

public:
	// Called in `OnBecomeRelevant`, `OnUpdate`, `OnInitialUpdate`, see RIDER-84952
	UFUNCTION(BlueprintCallable, meta = (BlueprintThreadSafe))
	void OnUpdateOrBecomeRelevant(const FAnimUpdateContext& InContext, const FAnimNodeReference& InNode);
};
