// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "RiderSourceCodeAccessor.h"
#include "HAL/PlatformProcess.h"
#include "DesktopPlatformModule.h"
#include "Internationalization/Regex.h"
#include "Misc/FileHelper.h"
#include "Serialization/JsonReader.h"
#include "Dom/JsonObject.h"
#include "Serialization/JsonSerializer.h"
#include "Misc/Paths.h"
#include "Misc/ScopeLock.h"
#include "Misc/UProjectInfo.h"
#include "Misc/App.h"

#if PLATFORM_WINDOWS
#include "Windows/AllowWindowsPlatformTypes.h"
#endif

#define LOCTEXT_NAMESPACE "RiderSourceCodeAccessor"

DEFINE_LOG_CATEGORY_STATIC(LogRiderAccessor, Log, All);

void FRiderSourceCodeAccessor::RefreshAvailability()
{
	// Find our program
	ExecutablePath = FindExecutablePath();

	// If we have an executable path, we certainly have it installed!
	if (!ExecutablePath.IsEmpty())
	{
		bHasRiderInstalled = true;
	}
	else
	{
		bHasRiderInstalled = false;
	}
}

bool FRiderSourceCodeAccessor::AddSourceFiles(const TArray<FString>& AbsoluteSourcePaths, const TArray<FString>& AvailableModules)
{
	return false;
}

bool FRiderSourceCodeAccessor::CanAccessSourceCode() const
{
	return bHasRiderInstalled;
}

bool FRiderSourceCodeAccessor::DoesSolutionExist() const
{
	const FString SolutionPath = GetSolutionPath();
	return FPaths::FileExists(SolutionPath);
}

FText FRiderSourceCodeAccessor::GetDescriptionText() const
{
	return LOCTEXT("RiderDisplayDesc", "Open source code files in Rider");
}

FName FRiderSourceCodeAccessor::GetFName() const
{
	return FName("RiderSourceCodeAccessor");
}

FText FRiderSourceCodeAccessor::GetNameText() const
{
	return LOCTEXT("RiderDisplayName", "Rider");
}

bool FRiderSourceCodeAccessor::OpenFileAtLine(const FString& FullPath, int32 LineNumber, int32 ColumnNumber)
{
	if (!bHasRiderInstalled)
	{
		return false;
	}

	const FString Path = FString::Printf(TEXT("\"%s\" --line %d \"%s\""), *FPaths::ConvertRelativePathToFull(*FPaths::ProjectDir()), LineNumber, *FullPath);

	FProcHandle Proc = FPlatformProcess::CreateProc(*ExecutablePath, *Path, true, true, false, nullptr, 0, nullptr, nullptr);
	if (!Proc.IsValid())
	{
		UE_LOG(LogRiderAccessor, Warning, TEXT("Opening file (%s) at a specific line failed."), *Path);
		FPlatformProcess::CloseProc(Proc);
		return false;
	}

	return true;
}

bool FRiderSourceCodeAccessor::OpenSolution()
{
	if (!bHasRiderInstalled)
	{
		return false;
	}

	const FString Path = FString::Printf(TEXT("\"%s\""), *FPaths::ConvertRelativePathToFull(*FPaths::ProjectDir()));

	FPlatformProcess::CreateProc(*ExecutablePath, *Path, true, true, false, nullptr, 0, nullptr, nullptr);

	return true;
}

bool FRiderSourceCodeAccessor::OpenSolutionAtPath(const FString& InSolutionPath)
{
	if (!bHasRiderInstalled)
	{
		return false;
	}

	FString CorrectSolutionPath = InSolutionPath;
	if (InSolutionPath.EndsWith(TEXT("UE4")))
	{
		CorrectSolutionPath = CorrectSolutionPath.Left(CorrectSolutionPath.Len() - 3);
	}

	return FPlatformProcess::CreateProc(*ExecutablePath, *CorrectSolutionPath, true, true, false, nullptr, 0, nullptr, nullptr).IsValid();
}

bool FRiderSourceCodeAccessor::OpenSourceFiles(const TArray<FString>& AbsoluteSourcePaths)
{
	if (!bHasRiderInstalled)
	{
		return false;
	}

	FString SourceFilesList = "";

	// Build our paths based on what unreal sends to be opened
	for (const auto& SourcePath : AbsoluteSourcePaths)
	{
		SourceFilesList = FString::Printf(TEXT("%s \"%s\""), *SourceFilesList, *SourcePath);
	}

	// Trim any whitespace on our source file list
	SourceFilesList.TrimStartInline();
	SourceFilesList.TrimEndInline();

	FProcHandle Proc = FPlatformProcess::CreateProc(*ExecutablePath, *SourceFilesList, true, false, false, nullptr, 0, nullptr, nullptr);
	if (!Proc.IsValid())
	{
		UE_LOG(LogRiderAccessor, Warning, TEXT("Opening the source file (%s) failed."), *SourceFilesList);
		FPlatformProcess::CloseProc(Proc);
		return false;
	}

	return true;
}

bool FRiderSourceCodeAccessor::SaveAllOpenDocuments() const
{
	//@todo.Rider This feature will be made available in 2017.3, till then we'll leave it commented out for a future PR
	// FProcHandle Proc = FPlatformProcess::CreateProc(*ExecutablePath, TEXT("save"), true, false,
	//                                                 false, nullptr, 0, nullptr, nullptr);

	// if (!Proc.IsValid())
	// {
	// 	FPlatformProcess::CloseProc(Proc);
	// 	return false;
	// }
	// return true;
	return false;
}

FString FRiderSourceCodeAccessor::FindExecutablePath()
{
#if PLATFORM_WINDOWS
	// Search from JetBrainsToolbox folder
	FString ToolboxBinPath;

	if (FWindowsPlatformMisc::QueryRegKey(HKEY_CURRENT_USER, TEXT("Software\\JetBrains s.r.o.\\JetBrainsToolbox\\"), TEXT(""), ToolboxBinPath))
	{
		FPaths::NormalizeDirectoryName(ToolboxBinPath);
		FString PatternString(TEXT("(.*)/bin"));
		FRegexPattern Pattern(PatternString);
		FRegexMatcher Matcher(Pattern, ToolboxBinPath);
		if (Matcher.FindNext())
		{
			FString ToolboxPath = Matcher.GetCaptureGroup(1);

			FString SettingJsonPath = FPaths::Combine(ToolboxPath, FString(".settings.json"));
			if (FPaths::FileExists(SettingJsonPath))
			{
				FString JsonStr;
				FFileHelper::LoadFileToString(JsonStr, *SettingJsonPath);
				TSharedRef<TJsonReader<TCHAR>> JsonReader = TJsonReaderFactory<TCHAR>::Create(JsonStr);
				TSharedPtr<FJsonObject> JsonObject = MakeShareable(new FJsonObject());
				if (FJsonSerializer::Deserialize(JsonReader, JsonObject) && JsonObject.IsValid())
				{
					FString InstallLocation;
					if (JsonObject->TryGetStringField(TEXT("install_location"), InstallLocation))
					{
						if (!InstallLocation.IsEmpty())
						{
							ToolboxPath = InstallLocation;
						}
					}
				}
			}

			FString RiderHome = FPaths::Combine(ToolboxPath, FString("apps"), FString("Rider"));
			if (FPaths::DirectoryExists(RiderHome))
			{
				TArray<FString> IDEPaths;
				IFileManager::Get().FindFilesRecursive(IDEPaths, *RiderHome, TEXT("rider64.exe"), true, false);
				if (IDEPaths.Num() > 0)
				{
					return IDEPaths[0];
				}
			}
		}
	}

	// Search from ProgID
	FString OpenCommand;
	if (!FWindowsPlatformMisc::QueryRegKey(HKEY_CURRENT_USER, TEXT("SOFTWARE\\Classes\\Applications\\rider64.exe\\shell\\open\\command\\"), TEXT(""), OpenCommand))
	{
		FWindowsPlatformMisc::QueryRegKey(HKEY_LOCAL_MACHINE, TEXT("SOFTWARE\\Classes\\Applications\\rider64.exe\\shell\\open\\command\\"), TEXT(""), OpenCommand);
	}

	FString PatternString(TEXT("\"(.*)\" \".*\""));
	FRegexPattern Pattern(PatternString);
	FRegexMatcher Matcher(Pattern, OpenCommand);
	if (Matcher.FindNext())
	{
		FString IDEPath = Matcher.GetCaptureGroup(1);
		if (FPaths::FileExists(IDEPath))
		{
			return IDEPath;
		}
	}

#elif  PLATFORM_MAC

	// Check for EAP
	NSURL* RiderPreviewURL = [[NSWorkspace sharedWorkspace] URLForApplicationWithBundleIdentifier:@"com.jetbrains.Rider-EAP"];
	if (RiderPreviewURL != nullptr)
	{
		return FString([RiderPreviewURL path]);
	}

	// Standard Rider Install
	NSURL* RiderURL = [[NSWorkspace sharedWorkspace] URLForApplicationWithBundleIdentifier:@"com.jetbrains.Rider"];
	if (RiderURL != nullptr)
	{
		return FString([RiderURL path]);
	}

	// Failsafe
	if (FPaths::FileExists(TEXT("/Applications/Rider.app/Contents/MacOS/Rider")))
	{
		return TEXT("/Applications/Rider.app/Contents/MacOS/Rider");
	}

#else

	// Linux Default Install
	if(FPaths::FileExists(TEXT("/opt/Rider/bin/Rider.sh")))
	{
		return TEXT("/opt/Rider/bin/Rider.sh");
	}
#endif

	// Nothing was found, return nothing as well
	return TEXT("");
}


FString FRiderSourceCodeAccessor::GetSolutionPath() const
{
	FScopeLock Lock(&CachedSolutionPathCriticalSection);

	if (IsInGameThread())
	{
		CachedSolutionPath = FPaths::ProjectDir();

		if (!FUProjectDictionary(FPaths::RootDir()).IsForeignProject(CachedSolutionPath))
		{
			CachedSolutionPath = FPaths::Combine(FPaths::RootDir(), FString("UE4") + ".sln");
		}
		else
		{
			FString BaseName = FApp::HasProjectName() ? FApp::GetProjectName() : FPaths::GetBaseFilename(CachedSolutionPath);
			CachedSolutionPath = FPaths::Combine(CachedSolutionPath, BaseName + ".sln");
		}
	}

	return CachedSolutionPath;
}

#undef LOCTEXT_NAMESPACE
