#pragma once

#include "RdEditorModel.Generated.h"
#include "lifetime/LifetimeDefinition.h"

#include "ModuleInterface.h"
#include "ModuleManager.h"

class RIDERLINK_API IRiderLinkModule : public IModuleInterface
{
public:
	static IRiderLinkModule& Get()
	{		
		return FModuleManager::GetModuleChecked<IRiderLinkModule>(GetModuleName());
	}

	static FName GetModuleName()
	{
		static const FName ModuleName = TEXT("RiderLink");
		return ModuleName;
	}

    // RiderLink API
	virtual rd::LifetimeDefinition CreateNestedLifetimeDefinition() const = 0;
	virtual void ViewModel(rd::Lifetime Lifetime, TFunction<void(rd::Lifetime, JetBrains::EditorPlugin::RdEditorModel const&)> Handler) = 0;
	virtual void QueueAction(TFunction<void()> Handler) = 0;
};
