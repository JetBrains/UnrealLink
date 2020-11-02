#include "RdConnection.hpp"

#include "ProtocolFactory.h"
#include "Model/Library/UE4Library/UE4Library.Generated.h"

RdConnection::RdConnection():
    SocketLifetimeDef{rd::Lifetime::Eternal()}
    , SocketLifetime{SocketLifetimeDef.lifetime}
    , Scheduler{SocketLifetime, "UnrealEditorScheduler"}
{
}

RdConnection::~RdConnection()
{
    SocketLifetimeDef.terminate();
}

void RdConnection::Init()
{
    Protocol = ProtocolFactory::Create(&Scheduler, SocketLifetime);
    Scheduler.queue([&]()
    {
        UnrealToBackendModel.connect(SocketLifetime, Protocol.Get());
        JetBrains::EditorPlugin::UE4Library::serializersOwner.registerSerializersCore(
            UnrealToBackendModel.get_serialization_context().get_serializers()
        );
    });
}

void RdConnection::Shutdown()
{
	Scheduler.flush();
    SocketLifetimeDef.terminate();
}
