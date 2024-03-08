#include "UnrealFunctions.h"
#include "Engine/BlueprintGeneratedClass.h"
#include "Templates/Casts.h"

UClass* RiderDebuggerSupport::FindClassForNode(const UObject* Object, const UFunction* Function)
{
    if (nullptr != Function) return Function->GetOwnerClass();

    if (nullptr != Object) return Object->GetClass();
    return nullptr;
}

UEdGraphNode* RiderDebuggerSupport::FindSourceNodeForCodeLocation(const UObject* Object, UFunction* Function)
{
#if WITH_EDITORONLY_DATA

    if (nullptr == Object) return nullptr;

    if (UBlueprintGeneratedClass* Class = Cast<UBlueprintGeneratedClass>(FindClassForNode(Object, Function)))
    {
        return Class->GetDebugData().
                      FindSourceNodeFromCodeLocation(Function, 0, true);
    }

#endif
    return nullptr;
}

FString RiderDebuggerSupport::GetClassNameWithoutSuffix(const UClass* Class)
{
    FString Result = TEXT("Null");
    if (nullptr == Class) return Result;

    Result = Class->GetName();
    if (nullptr != Class->ClassGeneratedBy)
    {
        Result.RemoveFromEnd(TEXT("_C"), ESearchCase::CaseSensitive);
    }

    return Result;
}

const UClass* RiderDebuggerSupport::CastToUClass(const UObject* Object)
{
    if (nullptr == Object) return nullptr;

    const auto& TypeOfObject = Object->GetClass();
    if (nullptr == TypeOfObject) return nullptr;

    const auto& TypeOfTypeOfObject = TypeOfObject->GetClass();
    if (nullptr == TypeOfTypeOfObject) return nullptr;

    const auto& TypeOfClass = TypeOfTypeOfObject->GetClass();
    if (nullptr == TypeOfClass) return nullptr;

    if (TypeOfClass != TypeOfTypeOfObject) return nullptr;

    return static_cast<const UClass*>(Object);
}