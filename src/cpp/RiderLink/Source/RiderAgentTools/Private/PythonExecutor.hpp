#pragma once
#include "RdEditorModel/RdEditorModel.Pregenerated.h"
#include "lifetime/Lifetime.h"

namespace PythonExecutor
{
    void BindTo(rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model);
}
