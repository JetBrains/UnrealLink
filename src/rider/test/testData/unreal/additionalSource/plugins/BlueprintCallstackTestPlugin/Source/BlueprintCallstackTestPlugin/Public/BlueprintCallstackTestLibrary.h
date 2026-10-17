#pragma once

#include "CoreMinimal.h"
#include "Kismet/BlueprintFunctionLibrary.h"
#include "BlueprintCallstackTestLibrary.generated.h"

/** The parent class of the generated Blueprint. */
UCLASS(Blueprintable)
class UBlueprintCallstackTestObject : public UObject
{
	GENERATED_BODY()
};

UCLASS()
class UBlueprintCallstackTestLibrary : public UBlueprintFunctionLibrary
{
	GENERATED_BODY()

public:
	/** The generated Blueprint calls this function. The Rider test stops here and reads the call stack. */
	UFUNCTION(BlueprintCallable, Category = "Rider|Tests")
	static void CalledFromBlueprint();
};
