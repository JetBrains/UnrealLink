#include "WideStringWrapper.h"

#include "HAL/UnrealMemory.h"


RiderDebuggerSupport::FWideStringWrapper::FWideStringWrapper(char* Buf, uint32 BufLen):
    AllocatedBuffer(Buf),
    AllocatedBufferLength(BufLen)
{
    if (AllocatedBuffer != nullptr && AllocatedBufferLength > LengthPrefixSize)
    {
        WideStringLength = reinterpret_cast<uint32*>(AllocatedBuffer);
        *WideStringLength = 0;
        PointerToString = reinterpret_cast<wchar_t*>(AllocatedBuffer + LengthPrefixSize);
        AvailableStringSpaceInBytes = AllocatedBufferLength - LengthPrefixSize;
    }
    else
    {
        WideStringLength = nullptr;
        PointerToString = nullptr;
        AvailableStringSpaceInBytes = 0;
    }
}

uint32 RiderDebuggerSupport::FWideStringWrapper::CopyFromNullTerminatedStr(const wchar_t* SourceStr, uint32 SourceStrLength) const
{
    if (nullptr == SourceStr || 0 == SourceStrLength) return 0;
    if (PointerToString == nullptr || AllocatedBufferLength <= LengthPrefixSize) return 0;

    uint32 LocalDataLength = SourceStrLength;

    while (LocalDataLength > 0 && SourceStr[LocalDataLength - 1] == L'\0')
    {
        --LocalDataLength;
    }

    uint32 BytesToCopy = LocalDataLength * sizeof(wchar_t);

    if (BytesToCopy > AvailableStringSpaceInBytes)
    {
        BytesToCopy = AvailableStringSpaceInBytes;
        LocalDataLength = BytesToCopy / sizeof(wchar_t);
    }

    *WideStringLength = LocalDataLength;

    FMemory::Memcpy(PointerToString, SourceStr, BytesToCopy);

    return LocalDataLength;
}
