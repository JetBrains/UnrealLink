#include "ProtocolFactory.h"

#include "scheduler/base/IScheduler.h"
#include "wire/SocketWire.h"

#include "HAL/PlatformFilemanager.h"
#include "Misc/App.h"
#include "Misc/FileHelper.h"
#include "Misc/Paths.h"

#if PLATFORM_WINDOWS
// ReSharper disable once CppUnusedIncludeDirective
#include "Windows/AllowWindowsPlatformTypes.h"
#include "Windows/PreWindowsApi.h"

#include "Windows/WindowsPlatformMisc.h"

#include "Windows/PostWindowsApi.h"
// ReSharper disable once CppUnusedIncludeDirective
#include "Windows/HideWindowsPlatformTypes.h"
#endif

#include "Runtime/Launch/Resources/Version.h"

static FString GetPathToPortsFolder()
{
    const FString EnvironmentVarName =
#if PLATFORM_WINDOWS
    TEXT("LOCALAPPDATA");
#else
    TEXT("HOME");
#endif
#if ENGINE_MAJOR_VERSION == 4 && ENGINE_MINOR_VERSION <= 20
    TCHAR CAppDataLocalPath[4096];
    FPlatformMisc::GetEnvironmentVariable(*EnvironmentVarName, CAppDataLocalPath, ARRAY_COUNT(CAppDataLocalPath));
    const FString FAppDataLocalPath = CAppDataLocalPath;
#else
    const FString FAppDataLocalPath = FPlatformMisc::GetEnvironmentVariable(*EnvironmentVarName);
#endif

    const FString PortFullDirectoryPath = FPaths::Combine(*FAppDataLocalPath,
#if PLATFORM_WINDOWS
        TEXT("Jetbrains"), TEXT("Rider"), TEXT("Unreal"), TEXT("Ports")
#elif PLATFORM_MAC
        TEXT("Library"), TEXT("Logs"), TEXT("Unreal Engine"), TEXT("Ports")
#else
        TEXT(".config"), TEXT("unrealEngine"), TEXT("Ports")
#endif
    );

    return PortFullDirectoryPath;
}

FString GetProjectName()
{
    FString ProjectNameNoExtension = FApp::GetProjectName();
    if(ProjectNameNoExtension.IsEmpty())
        ProjectNameNoExtension = TEXT("<ENGINE>");
    return ProjectNameNoExtension + TEXT(".uproject");
}

std::shared_ptr<rd::SocketWire::Server> ProtocolFactory::CreateWire(rd::IScheduler* Scheduler, rd::Lifetime SocketLifetime)
{
    const FString ProjectName = GetProjectName();

    spdlog::set_level(spdlog::level::err);
    return std::make_shared<rd::SocketWire::Server>(SocketLifetime, Scheduler, 0,
                                                         TCHAR_TO_UTF8(*FString::Printf(TEXT("UnrealEditorServer-%s"),
                                                             *ProjectName)));
}


TUniquePtr<rd::Protocol> ProtocolFactory::CreateProtocol(rd::IScheduler* Scheduler, rd::Lifetime SocketLifetime, std::shared_ptr<rd::SocketWire::Server> wire)
{
    const FString ProjectName = GetProjectName();

    auto protocol = MakeUnique<rd::Protocol>(rd::Identities::SERVER, Scheduler, wire, SocketLifetime);

    auto& PlatformFile = FPlatformFileManager::Get().GetPlatformFile();
    const FString PortFullDirectoryPath = GetPathToPortsFolder();
    if (PlatformFile.CreateDirectoryTree(*PortFullDirectoryPath) && !IsRunningCommandlet())
    {
        const FString TmpPortFile = TEXT("~") + ProjectName;
        const FString TmpPortFileFullPath = FPaths::Combine(*PortFullDirectoryPath, *TmpPortFile);
        FFileHelper::SaveStringToFile(FString::FromInt(wire->port), *TmpPortFileFullPath);
        const FString PortFileFullPath = FPaths::Combine(*PortFullDirectoryPath, *ProjectName);
        IFileManager::Get().Move(*PortFileFullPath, *TmpPortFileFullPath, true, true);
    }
    return protocol;
}
