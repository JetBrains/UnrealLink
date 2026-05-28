#pragma once

#include "lifetime/Lifetime.h"

namespace JetBrains
{
    namespace EditorPlugin
    {
        class RdEditorModel;
    }
}

class SceneActorSpawner
{
public:
    static void BindTo(rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model);
};
