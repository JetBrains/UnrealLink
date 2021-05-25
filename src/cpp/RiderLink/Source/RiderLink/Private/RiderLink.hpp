#pragma once

#include "IRiderLink.hpp"
#include "impl/RdProperty.h"
#include "lifetime/LifetimeDefinition.h"
#include "scheduler/SingleThreadScheduler.h"
#include "wire/SocketWire.h"

#include "Logging/LogMacros.h"
#include "Logging/LogVerbosity.h"
#include "Modules/ModuleManager.h"

#include "RdEditorModel/RdEditorModel.Generated.h"

namespace rd
{
	class Protocol;
}

DECLARE_LOG_CATEGORY_EXTERN(FLogRiderLinkModule, Log, All);

class RIDERLINK_API FRiderLinkModule : public IRiderLinkModule
{
public:
	FRiderLinkModule() = default;
	virtual ~FRiderLinkModule() override = default;

	/** IModuleInterface implementation */
	virtual void StartupModule() override;
	virtual void ShutdownModule() override;
	virtual bool SupportsDynamicReloading() override;

	virtual rd::LifetimeDefinition CreateNestedLifetimeDefinition(const std::string& name) const override
	{
		return rd::LifetimeDefinition{ModuleLifetimeDef.lifetime, name};
	}

	virtual void ViewModel(rd::Lifetime Lifetime,
	                       TFunction<void(rd::Lifetime,
	                                      JetBrains::EditorPlugin::RdEditorModel const&)> Handler) override;
	virtual void QueueAction(TFunction<void()> Handler) override;

private:
	void InitProtocol();

	rd::LifetimeDefinition ModuleLifetimeDef{false, "RiderLink"};
	rd::SingleThreadScheduler Scheduler{ModuleLifetimeDef.lifetime, "MainScheduler"};
	rd::LifetimeDefinition WireLifetimeDef{ModuleLifetimeDef.lifetime, "Wire"};
	TUniquePtr<rd::Protocol> Protocol;
	rd::RdProperty<bool> RdIsModelAlive;
	TUniquePtr<JetBrains::EditorPlugin::RdEditorModel> EditorModel;
};
