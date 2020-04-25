#include "RdConnection.hpp"

// ReSharper disable once CppUnusedIncludeDirective
// #include "Windows/AllowWindowsPlatformTypes.h"

#include "ProtocolFactory.h"
#include "RdEditorProtocol/UE4Library/UE4Library.h"

RdConnection::RdConnection():
    LifetimeDef{rd::Lifetime::Eternal()}
    , SocketLifetimeDef{rd::Lifetime::Eternal()}
    , Lifetime{LifetimeDef.lifetime}
    , SocketLifetime{SocketLifetimeDef.lifetime}
    , Scheduler{SocketLifetime, "UnrealEditorScheduler"} {}

RdConnection::~RdConnection() {
    SocketLifetimeDef.terminate();
    LifetimeDef.terminate();
}

void RdConnection::Init() {
    Protocol = ProtocolFactory::Create(&Scheduler, SocketLifetime);
    UnrealToBackendModel.connect(Lifetime, Protocol.Get());
    Jetbrains::EditorPlugin::UE4Library::serializersOwner.registerSerializersCore(
        UnrealToBackendModel.get_serialization_context().get_serializers());
}

void RdConnection::Shutdown()
{
    LifetimeDef.terminate();
    SocketLifetimeDef.terminate();
}

// ReSharper disable once CppUnusedIncludeDirective
// #include "Windows/HideWindowsPlatformTypes.h"
