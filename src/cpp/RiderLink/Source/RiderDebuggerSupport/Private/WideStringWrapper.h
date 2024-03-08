#pragma once

#include "HAL/Platform.h"

namespace RiderDebuggerSupport
{
    class FWideStringWrapper
    {
        char* AllocatedBuffer; // Raw buffer to store data as bytes
        uint32 AllocatedBufferLength; // Total buffer length in bytes, including space for length prefix
        uint32* WideStringLength;
        uint32 AvailableStringSpaceInBytes;
        wchar_t* PointerToString; // Pointer to the start of the string within AllocatedBuffer

        static constexpr uint32 LengthPrefixSize = sizeof(uint32); // Size of the length prefix in bytes
    public:
        FWideStringWrapper(char* Buf, uint32 BufLen);
        uint32 CopyFromNullTerminatedStr(const wchar_t* SourceStr, uint32 SourceStrLength) const;
    };
}