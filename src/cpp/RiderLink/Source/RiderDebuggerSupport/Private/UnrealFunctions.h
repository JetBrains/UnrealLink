#pragma once


class UEdGraphNode;
class UFunction;
class UObject;
class UClass;
class FString;

namespace RiderDebuggerSupport
{
    UClass* FindClassForNode(const UObject* Object, const UFunction* Function);
    UEdGraphNode* FindSourceNodeForCodeLocation(const UObject* Object, UFunction* Function);
    FString GetClassNameWithoutSuffix(const UClass* Class);
    const UClass* CastToUClass(const UObject* Object);
}
