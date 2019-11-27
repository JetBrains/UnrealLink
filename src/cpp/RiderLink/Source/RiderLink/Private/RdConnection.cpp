#include "RdConnection.hpp"

#include "UE4TypesMarshallers.h"

#include "SocketWire.h"
#include "Protocol.h"

#include "Windows/AllowWindowsPlatformTypes.h"

#include "Misc/FileHelper.h"
#include "Misc/Paths.h"
#include "Runtime/CoreUObject/Public/UObject/Class.h"
#include "GeneralProjectSettings.h"
#include "HAL/PlatformFilemanager.h"
#include "Misc/App.h"
#include "Windows/WindowsPlatformMisc.h"

#include "BlueprintProvider.h"


constexpr TCHAR PORT_FILE_NAME[] = TEXT("UnrealProtocolPort.txt");
constexpr TCHAR CLOSED_FILE_EXTENSION[] = TEXT(".closed");

RdConnection::RdConnection():
	lifetimeDef{rd::Lifetime::Eternal()}
	, socketLifetimeDef{rd::Lifetime::Eternal()}
	, lifetime{lifetimeDef.lifetime}
	, socketLifetime{socketLifetimeDef.lifetime}
	, scheduler{/*lifetime, "UnrealEditorScheduler"*/} {}

RdConnection::~RdConnection() {
	socketLifetimeDef.terminate();
	lifetimeDef.terminate();
}

void RdConnection::init() {
	_CrtDbgBreak();

	scheduler.queue([this] {

#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 20
		TCHAR CAppDataLocalPath[4096];
		FPlatformMisc::GetEnvironmentVariable(TEXT("LOCALAPPDATA"), CAppDataLocalPath, ARRAY_COUNT(CAppDataLocalPath));
		FString FAppDataLocalPath = CAppDataLocalPath;
#else
		const FString FAppDataLocalPath = FPlatformMisc::GetEnvironmentVariable(TEXT("LOCALAPPDATA"));
#endif

		const FString ProjectName = FApp::GetProjectName();
		const FString PortFullDirectoryPath = FPaths::Combine(*FAppDataLocalPath, TEXT("Jetbrains"), TEXT("Rider"),
		                                                      TEXT("Unreal"), *ProjectName, TEXT("Ports"));
		const FString PortFileFullPath = FPaths::Combine(PortFullDirectoryPath, PORT_FILE_NAME);
		const FString PortFileClosedPath = FPaths::Combine(PortFullDirectoryPath,
		                                                   FString(PORT_FILE_NAME).Append(CLOSED_FILE_EXTENSION));
		auto wire = std::make_shared<rd::SocketWire::Server>(socketLifetime, &scheduler, 0,
		                                                     TCHAR_TO_UTF8(
			                                                     *FString::Printf(TEXT("UnrealEditorServer-%s"), *
				                                                     ProjectName)));
		protocol = std::make_unique<rd::Protocol>(rd::Identities::SERVER, &scheduler, wire, lifetime);
		unrealToBackendModel.connect(lifetime, protocol.get());

		auto& PlatformFile = FPlatformFileManager::Get().GetPlatformFile();
		if (PlatformFile.CreateDirectoryTree(*PortFullDirectoryPath)) {
			FFileHelper::SaveStringToFile(FString::FromInt(wire->port), *PortFileFullPath);
			FFileHelper::SaveStringToFile("", *PortFileClosedPath);
		}
		wire->connected.advise(socketLifetime, [this](bool value) {
			if (value) {
				//connected to R#
			}
			else {
				//R# disconnected 
			}
		});

		lifetime->add_action([&, PortFileFullPath, PortFileClosedPath] {
			if (!PlatformFile.DeleteFile(*PortFileFullPath)) {
				//log error
			}
			if (!PlatformFile.DeleteFile(*PortFileClosedPath)) {
				//log error
			}
		});

		unrealToBackendModel.get_isBlueprint().set([](Jetbrains::EditorPlugin::BlueprintStruct const& s) {
			return BluePrintProvider::IsBlueprint(s.get_pathName(), s.get_graphName());
		});
		unrealToBackendModel.get_navigate().advise(lifetime, [this](Jetbrains::EditorPlugin::BlueprintStruct const& s) {
			BluePrintProvider::OpenBlueprint(s.get_pathName(), s.get_graphName());
		});
	});
}

#include "Windows/HideWindowsPlatformTypes.h"
