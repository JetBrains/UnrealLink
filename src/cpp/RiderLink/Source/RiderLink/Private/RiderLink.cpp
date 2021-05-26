#include "RiderLink.hpp"

#include "ProtocolFactory.h"
#include "UE4Library/UE4Library.Generated.h"

#include "Modules/ModuleManager.h"
#include "HAL/Platform.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLinkModule);

IMPLEMENT_MODULE(FRiderLinkModule, RiderLink);

void FRiderLinkModule::ShutdownModule()
{
	UE_LOG(FLogRiderLinkModule, Verbose, TEXT("RiderLink SHUTDOWN START"));
	ModuleLifetimeDef.terminate();
	UE_LOG(FLogRiderLinkModule, Verbose, TEXT("RiderLink SHUTDOWN FINISH"));
}

void FRiderLinkModule::StartupModule()
{
	UE_LOG(FLogRiderLinkModule, Verbose, TEXT("RiderLink STARTUP START"));
	Scheduler.queue([this]()
	{
		InitProtocol();
	});
	UE_LOG(FLogRiderLinkModule, Verbose, TEXT("RiderLink STARTUP FINISH"));
}

void FRiderLinkModule::InitProtocol()
{
	WireLifetimeDef = MakeUnique<rd::LifetimeDefinition>(ModuleLifetimeDef.lifetime);
	rd::Lifetime WireLifetime = WireLifetimeDef->lifetime;
	std::shared_ptr<rd::SocketWire::Server> Wire = ProtocolFactory::CreateWire(&Scheduler, WireLifetime);
	Protocol = ProtocolFactory::CreateProtocol(&Scheduler, WireLifetime.create_nested(), Wire);
	// Exception fired for Server::Base::~Base() when trying to invoke it this way
//	WireLifetime->add_action([this]()
//	{
//		if (!ModuleLifetimeDef.is_terminated())
//		{
//			Scheduler.queue([this]()
//			{
//				InitProtocol();
//			});
//		}
//	});
	Protocol->wire->connected.view(WireLifetime, [this](rd::Lifetime ConnectionLifetime, bool const& IsConnected)
	{
		Scheduler.queue([this, ConnectionLifetime, IsConnected]()
		{
			if (!IsConnected) return;

			EditorModel = MakeUnique<JetBrains::EditorPlugin::RdEditorModel>();
			EditorModel->connect(ConnectionLifetime, Protocol.Get());
			JetBrains::EditorPlugin::UE4Library::serializersOwner.registerSerializersCore(
				EditorModel->get_serialization_context().get_serializers()
			);
			ConnectionLifetime->add_action([&]() mutable
			{
				Scheduler.queue([&]()mutable
				{
					RdIsModelAlive.set(false);
					EditorModel.Reset();
					// WireLifetimeDef->terminate();
				});
			});
			RdIsModelAlive.set(true);
		});
	});
}

bool FRiderLinkModule::SupportsDynamicReloading() { return true; }


// Can't place RdEditorModel or TUniquePtr<RdEditorModel> into RdProperty.
// Have to resort to RdProperty<bool> and change it before creating new RdEditorModel
void FRiderLinkModule::ViewModel(rd::Lifetime Lifetime,
                                 TFunction<void(rd::Lifetime, JetBrains::EditorPlugin::RdEditorModel const&)> Handler)
{
	Scheduler.invoke_or_queue([this, Lifetime, Handler]
	{
		RdIsModelAlive.view(Lifetime, [this, Handler](rd::Lifetime ModelLifetime, bool const& Cond)
		{
			if (Cond) Handler(ModelLifetime, *EditorModel.Get());
		});
	});
}

void FRiderLinkModule::QueueAction(TFunction<void()> Handler)
{
	Scheduler.invoke_or_queue([this, Handler]
	{
		Handler();
	});
}

#undef LOCTEXT_NAMESPACE
