#include "UObject/Class.h"
#include "DebugLogger.h"
#include "UnrealFunctions.h"
#include "UObject/UObjectBaseUtility.h"
#include "WideStringWrapper.h"
#include "EdGraph/EdGraphNode.h"

namespace RiderDebuggerSupport
{
    constexpr int StringBufferSizeInWideChars = 1024;
    constexpr int GWideCharSizeInBytes = sizeof(wchar_t);
    constexpr int GStringSizeInBytes = StringBufferSizeInWideChars * GWideCharSizeInBytes;
    constexpr int GLengthSizeInBytes = sizeof(uint32_t);
    constexpr int GResultCodeSizeInBytes = sizeof(uint32_t);
    constexpr int GStringEntrySizeInBytes = GStringSizeInBytes + GLengthSizeInBytes;
    constexpr int GFirstStringOffset = GLengthSizeInBytes;
    constexpr int GSecondStringOffset = GFirstStringOffset + GStringEntrySizeInBytes;
    constexpr int GThirdStringOffset = GSecondStringOffset + GStringEntrySizeInBytes;
    constexpr int GBufferSizeInBytes = GResultCodeSizeInBytes + 3 * GStringEntrySizeInBytes;
}

/* Start Identifiers available from IDEA */

extern "C" DLLEXPORT void __stdcall RiderDebuggerSupport_GetBlueprintFunction(void* PFunction, void* PContext);

struct FJbCallContextModule
{
    void* Context;
    void* Function;
} RiderDebuggerSupportBlueprintFunctionCallContext;

int RiderDebuggerSupportModuleVersion = 1346;

char RiderDebuggerSupportBlueprintFunctionBuffer[RiderDebuggerSupport::GBufferSizeInBytes] = {0};
int RiderDebuggerSupportBlueprintBufferSizeInBytes = RiderDebuggerSupport::GBufferSizeInBytes;
int RiderDebuggerSupportBlueprintStringBufferSizeInChars = RiderDebuggerSupport::StringBufferSizeInWideChars;
int RiderDebuggerSupportBlueprintWideCharSizeInBytes = RiderDebuggerSupport::GWideCharSizeInBytes;

/* End Identifiers available from IDEA */

namespace RiderDebuggerSupport
{
    static FWideStringWrapper GJbFullNameWrapper(RiderDebuggerSupportBlueprintFunctionBuffer + GFirstStringOffset, GStringEntrySizeInBytes);
    static FWideStringWrapper GJbScopeDisplayNameWrapper(RiderDebuggerSupportBlueprintFunctionBuffer + GSecondStringOffset, GStringEntrySizeInBytes);
    static FWideStringWrapper GJbFunctionDisplayNameWrapper(RiderDebuggerSupportBlueprintFunctionBuffer + GThirdStringOffset, GStringEntrySizeInBytes);

    uint32_t* GJbPOperationResultCode = reinterpret_cast<uint32_t*>(RiderDebuggerSupportBlueprintFunctionBuffer);

    static void SetLastExecutedLine(const uint16 LineNumber)
    {
        *GJbPOperationResultCode = (*GJbPOperationResultCode & 0xFFFF0000) | LineNumber;
    }

    static void SetResultCodeFlag(const uint8 FlagOffset)
    {
        *GJbPOperationResultCode |= 1 << FlagOffset;
    }
}

void RiderDebuggerSupport_GetBlueprintFunction(void* PFunction, void* PContext)
{
    using namespace RiderDebuggerSupport;

    SendLogToDebugger("Called %s: Context=%p Function=%p", __func__, PContext, PFunction);
    *GJbPOperationResultCode = 0;

    const UObject* Context = static_cast<UObject*>(PContext);
    UFunction* Function = static_cast<UFunction*>(PFunction);

    if (nullptr == Context)
    {
        SendLogToDebugger("Context is null");
        SetLastExecutedLine(__LINE__);
        return;
    }

    if (nullptr == Function)
    {
        SendLogToDebugger("Function is null");
        SetLastExecutedLine(__LINE__);
        return;
    }
    SetLastExecutedLine(__LINE__);

    FString FullName;
    SetLastExecutedLine(__LINE__);

    Function->GetFullName(nullptr, FullName, EObjectFullNameFlags::None);
    GJbFullNameWrapper.CopyFromNullTerminatedStr(
        GetData(FullName), FullName.Len());
    SetLastExecutedLine(__LINE__);

    const auto Outer = Function->GetOuter();

    SendLogToDebugger("Trying to get SourceClass");

    const UClass* SourceClass = CastToUClass(Outer);

    SendLogToDebugger("SourceClass=%p", SourceClass);

    SetLastExecutedLine(__LINE__);
    if (nullptr != SourceClass)
    {
        constexpr int SourceCodeNotNullFlag = 16;
        SetResultCodeFlag(SourceCodeNotNullFlag);
    }

    FString SourceClassDisplayName = GetClassNameWithoutSuffix(SourceClass);
    SendLogToDebugger(
        "SourceClassDisplayName length=%u, str_ptr=%p",
        SourceClassDisplayName.Len(), GetData(SourceClassDisplayName));
    SetLastExecutedLine(__LINE__);

    SendLogToDebugger("Trying to get outerDisplayName");
    FString OuterDisplayName = FText::FromName(Outer->GetFName()).ToString();
    SendLogToDebugger(
        "OuterDisplayName length=%u, str_ptr=%p",
        OuterDisplayName.Len(), GetData(OuterDisplayName));

    const auto ScopeDisplayName = SourceClass ? &SourceClassDisplayName : &OuterDisplayName;
    GJbScopeDisplayNameWrapper.CopyFromNullTerminatedStr(
        GetData(*ScopeDisplayName), ScopeDisplayName->Len());
    SetLastExecutedLine(__LINE__);

    auto FunctionDisplayName = FText::FromName(Function->GetFName()).ToString();
    GJbFunctionDisplayNameWrapper.CopyFromNullTerminatedStr(GetData(FunctionDisplayName), FunctionDisplayName.Len());
    SetLastExecutedLine(__LINE__);

#if WITH_EDITORONLY_DATA
    if (SourceClass)
    {
        const auto GraphNode = FindSourceNodeForCodeLocation(Context, Function);
        SetLastExecutedLine(__LINE__);

        if (nullptr != GraphNode)
        {
            constexpr int SourceCodeNotNullFlag = 17;
            SetResultCodeFlag(SourceCodeNotNullFlag);

            const FText NodeTitle = GraphNode->GetNodeTitle(ENodeTitleType::Type::ListView);
            SetLastExecutedLine(__LINE__);

            FString NodeTitleStr = NodeTitle.ToString();
            SetLastExecutedLine(__LINE__);

            GJbFunctionDisplayNameWrapper.CopyFromNullTerminatedStr(
                GetData(NodeTitleStr), NodeTitleStr.Len());
            SetLastExecutedLine(__LINE__);
        }
    }
#endif
}