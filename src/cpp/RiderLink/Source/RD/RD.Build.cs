// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

using System;
using System.IO;
using System.Runtime.Remoting.Contexts;
using UnrealBuildTool;

public class RD : ModuleRules
{
	public RD(ReadOnlyTargetRules Target) : base(Target)
	{
		PublicDependencyModuleNames.Add("Core");
		bUseRTTI = true;
		bEnforceIWYU = false;
		
#if UE_4_22_OR_LATER
		PCHUsage = PCHUsageMode.NoPCHs;
		CppStandard = CppStandardVersion.Cpp14;
#else
		PCHUsage = PCHUsageMode.NoSharedPCHs;
#endif
		
#if UE_4_24_OR_LATER
		ShadowVariableWarningLevel = WarningLevel.Off;
		bUseUnity = false;
#else
		bEnableShadowVariableWarnings = false;
		bFasterWithoutUnity = true;
#endif

		if (Target.Platform == UnrealTargetPlatform.Win64)
		{
			PublicDefinitions.Add("_WINSOCK_DEPRECATED_NO_WARNINGS");
			PublicDefinitions.Add("_CRT_SECURE_NO_WARNINGS");
			PublicDefinitions.Add("_CRT_NONSTDC_NO_DEPRECATE");
			PrivateDefinitions.Add("WIN32_LEAN_AND_MEAN");
		}

		// Common dependencies
		PrivateDefinitions.Add("FMT_EXPORT");
		
		PublicDefinitions.Add("SPDLOG_NO_EXCEPTIONS");
		PublicDefinitions.Add("SPDLOG_COMPILED_LIB");
		PublicDefinitions.Add("nssv_CONFIG_SELECT_STRING_VIEW=nssv_STRING_VIEW_NONSTD");
		PublicDefinitions.Add("rd_framework_cpp_EXPORTS");
		PublicDefinitions.Add("rd_core_cpp_EXPORTS");
		PublicDefinitions.Add("spdlog_EXPORTS");
		PublicDefinitions.Add("FMT_SHARED");
		PublicDefinitions.Add("SPDLOG_SHARED_LIB");
		PublicDefinitions.Add("SPDLOG_COMPILED_LIB");

		string[] Paths = {
			"src",
			"src/rd_core_cpp",
			"src/rd_core_cpp/src/main",
			
			"src/rd_framework_cpp",
			"src/rd_framework_cpp/src/main",
			"src/rd_framework_cpp/src/main/util",
			
			"src/rd_gen_cpp/src",
			
			"thirdparty",
			"thirdparty/ordered-map/include",
			"thirdparty/optional/tl",
			"thirdparty/variant/include",
			"thirdparty/string-view-lite/include",
			"thirdparty/spdlog/include",
			"thirdparty/clsocket/src",
			"thirdparty/CTPL/include"
		};
		
		foreach(var Item in Paths)
		{
			PublicIncludePaths.Add(Path.Combine(ModuleDirectory, Item));
		}
	}
}
