// Fill out your copyright notice in the Description page of Project Settings.

#pragma once

#include "CoreMinimal.h"
#include "CoreUObject/Public/UObject/Object.h"
#include "Engine/Classes/Commandlets/Commandlet.h"
#include "RDRunTestsCommandlet.generated.h"

class FAutomationTestInfo;
class FAutomationTestExecutionInfo;

UCLASS()
class URDRunTestsCommandlet : public UCommandlet
{
	GENERATED_BODY()
public:
	URDRunTestsCommandlet();
	virtual int32 Main(const FString& Params) override;
private:
	void AppendTestExecutionInfo(const FAutomationTestInfo& TestInfo, const FAutomationTestExecutionInfo& ExecutionInfo, TArray<FString>& JUnitReport);
	FString EscapeXmlCData(const FString& Value);
	FString EscapeXmlAttr(const FString& Value);
};
