#include "RdConnection.hpp"

#include "Misc/FileHelper.h"

uint16_t find_free_port() {
	CPassiveSocket fake_server;
	fake_server.Initialize();
	fake_server.Listen("127.0.0.1", 0);
	uint16_t port = fake_server.GetServerPort();

	return port;
}


RdConnection::RdConnection():
	  lifetimeDef{ Lifetime::Eternal() }
	, socketLifetimeDef{ Lifetime::Eternal() }
	, lifetime{ lifetimeDef.lifetime}
	, socketLifetime{socketLifetimeDef.lifetime}
{}
RdConnection::~RdConnection(){}

void RdConnection::init()
{
	uint16_t port = 0;
	TCHAR AppDataLocalPath[4096];
	FPlatformMisc::GetEnvironmentVariable(TEXT("LOCALAPPDATA"), AppDataLocalPath, ARRAY_COUNT(AppDataLocalPath));
	auto riderLinkTxtPath = FString::Printf(TEXT("%s/RiderLink.txt"), AppDataLocalPath);
	if (FPaths::FileExists(riderLinkTxtPath))
	{
		FString result;
		FFileHelper::LoadFileToString(result, *riderLinkTxtPath);
		port = FCString::Atoi(*result);
	}
	else
	{
		port = find_free_port();
		FFileHelper::SaveStringToFile(FString::FromInt(port), *riderLinkTxtPath);
	}

	wire = std::make_shared<SocketWire::Client>(lifetime, &clientScheduler, port, "UnrealClient");
	clientProtocol = std::make_unique<Protocol>(Identities(Identities::CLIENT), &clientScheduler, wire);
	unrealToBackendModel.connect(lifetime, clientProtocol.get());

}
