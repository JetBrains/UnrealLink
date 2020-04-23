#include "ProtocolFactory.h"

#include "Windows/AllowWindowsPlatformTypes.h"

#include "Misc/FileHelper.h"
#include "Misc/Paths.h"
#include "Runtime/CoreUObject/Public/UObject/Class.h"
#include "GeneralProjectSettings.h"
#include "HAL/PlatformFilemanager.h"
#include "Misc/App.h"
#include "Windows/WindowsPlatformMisc.h"

#include "Windows/HideWindowsPlatformTypes.h"

#include "SocketWire.h"

TUniquePtr<rd::Protocol> ProtocolFactory::create(rd::IScheduler & scheduler, rd::Lifetime socketLifetime) {
    

#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 20
		TCHAR CAppDataLocalPath[4096];
		FPlatformMisc::GetEnvironmentVariable(TEXT("LOCALAPPDATA"), CAppDataLocalPath, ARRAY_COUNT(CAppDataLocalPath));
		FString FAppDataLocalPath = CAppDataLocalPath;
#else
        const FString FAppDataLocalPath = FPlatformMisc::GetEnvironmentVariable(TEXT("LOCALAPPDATA"));
#endif

        const FString ProjectName = FApp::GetProjectName();
        const FString PortFullDirectoryPath = FPaths::Combine(*FAppDataLocalPath, TEXT("Jetbrains"), TEXT("Rider"),
                                                              TEXT("Unreal"), TEXT("Ports"));
        const FString PortFileFullPath = FPaths::Combine(PortFullDirectoryPath, *ProjectName);
        
        rd::minimum_level_to_log = rd::LogLevel::Fatal;
        auto wire = std::make_shared<rd::SocketWire::Server>(socketLifetime, &scheduler, 0,
                                                             TCHAR_TO_UTF8(
                                                                 *FString::Printf(TEXT("UnrealEditorServer-%s"), *
                                                                     ProjectName)));
        auto protocol = MakeUnique<rd::Protocol>(rd::Identities::SERVER, &scheduler, wire, socketLifetime);

        auto& PlatformFile = FPlatformFileManager::Get().GetPlatformFile();
        if (PlatformFile.CreateDirectoryTree(*PortFullDirectoryPath)) {
            FFileHelper::SaveStringToFile(FString::FromInt(wire->port), *PortFileFullPath);
        }
        wire->connected.advise(socketLifetime, [](bool value) {
            if (value) {
                //connected to R#
            }
            else {
                //R# disconnected
            }
        });

        socketLifetime->add_action([&, PortFileFullPath] {
            if (!PlatformFile.DeleteFile(*PortFileFullPath)) {
                //log error
            }
        });
    return protocol;
}
