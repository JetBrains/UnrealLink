#pragma once

#include "CoreMinimal.h"

namespace PythonRunContext
{
	// Refresh the progress dialog for the running script and report whether it should keep
	// going. Returns false once the user has pressed Cancel; `rider_progress.tick()` turns
	// that into a ScriptAborted. Returns true when no script is running under the tool, so
	// a script executed from the editor's own Python console is unaffected.
	bool ProgressTick(const FString& Message);
}
