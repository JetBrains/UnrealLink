#include "BlueprintCallstackTestLibrary.h"

void UBlueprintCallstackTestLibrary::CalledFromBlueprint()
{
	UE_LOG(LogTemp, Log, TEXT("BlueprintCallstackTest: CalledFromBlueprint")); // @breakpoint
}
