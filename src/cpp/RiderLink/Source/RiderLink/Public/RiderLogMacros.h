#pragma once

#include "Logging/LogMacros.h"
#include "Runtime/Launch/Resources/Version.h"

// Compatibility layer for engine logging macros.
//
// UE 6.0 deprecated UE_LOG in favour of UE_LOGF, which take a
// UTF-8 format string literal (no TEXT() wrapper). 
// All logging goes through RIDERLINK_LOG, which map to the proper
// engine macro for the target engine version.
//
// Usage rules for the Format argument:
//   * pass a plain narrow literal, without TEXT();
//   * use %ls for TCHAR strings (TCHAR*, *FString) - valid in both branches;
//   * use %hs for ANSICHAR strings - valid in both branches.

#if ENGINE_MAJOR_VERSION >= 6

	#define RIDERLINK_LOG(CategoryName, Verbosity, Format, ...) \
		UE_LOGF(CategoryName, Verbosity, Format, ##__VA_ARGS__)

#else

	#define RIDERLINK_LOG(CategoryName, Verbosity, Format, ...) \
		UE_LOG(CategoryName, Verbosity, TEXT(Format), ##__VA_ARGS__)

#endif
