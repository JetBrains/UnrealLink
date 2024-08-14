#include "DebugLogger.h"

#define JB_DEBUG_MODE 0
#define LOG_DBG_PREFIX "JB_UNR_BP_DBG_MSG>"
#define LOG_DBG_PREFIX_LENGTH (sizeof(LOG_DBG_PREFIX) - 1)

#if JB_DEBUG_MODE
#include "UObject/UnrealType.h"

// Avoid including windows.h
extern "C" __declspec(dllimport) void __stdcall OutputDebugStringA(const char* LpOutputString);

#endif

void RiderDebuggerSupport::SendLogToDebugger(
#if !JB_DEBUG_MODE && __cplusplus >= 201703L
    [[maybe_unused]]
#endif
    const char* FormatStr, ...)
{
#if JB_DEBUG_MODE

    va_list Args;
    va_start(Args, FormatStr);

    char OutputBuffer[1024] = {LOG_DBG_PREFIX};

    char* FormatBuffer = OutputBuffer + LOG_DBG_PREFIX_LENGTH;
    constexpr int FormatBufferSize = sizeof(OutputBuffer) - LOG_DBG_PREFIX_LENGTH;

    const auto Res = vsprintf_s(FormatBuffer, FormatBufferSize, FormatStr, Args);

    if (Res > 0)
        OutputDebugStringA(OutputBuffer);
    else
        OutputDebugStringA("Error in SendLogToDebugger");

    va_end(Args);
#endif
}