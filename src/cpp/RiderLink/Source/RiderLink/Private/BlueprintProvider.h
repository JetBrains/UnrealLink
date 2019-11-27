#pragma once

#include "Framework/Text/SlateHyperlinkRun.h"

class BluePrintProvider {
public:
	static bool IsBlueprint(FString const& Path, FString const& Name);

	static void OpenBlueprint(FString const &path, FString const& name);
};