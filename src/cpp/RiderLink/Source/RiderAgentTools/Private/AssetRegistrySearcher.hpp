#pragma once

#include "lifetime/Lifetime.h"

namespace JetBrains
{
    namespace EditorPlugin
    {
        class RdEditorModel;
    }
}

class AssetRegistrySearcher
{
public:
    static void BindTo(rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model);
};
