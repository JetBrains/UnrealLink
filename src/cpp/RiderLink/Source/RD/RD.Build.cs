// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

using System;
using System.IO;
using UnrealBuildTool;

public class RD : ModuleRules
{
	public RD(ReadOnlyTargetRules Target) : base(Target)
	{
		Type = ModuleType.External;

		bUseRTTI = true;
		
		var Toolchain = "";
		var Platform = "";
		if (Target.Platform == UnrealTargetPlatform.Win64)
		{
			Platform = "Win";
			switch (Target.WindowsPlatform.Compiler)
			{
				case WindowsCompiler.VisualStudio2017:
					Toolchain = "win-vs17";
					break;
#if UE_4_22_OR_LATER
				case WindowsCompiler.VisualStudio2019:
					Toolchain = "win-vs19";
					break;
#endif					
#if UE_4_21_OR_LATER
				case WindowsCompiler.Clang:
					Toolchain = "win-clang";
					throw new NotImplementedException();
#endif
				default:
					throw new NotImplementedException();
			}
		}
		else if (Target.Platform == UnrealTargetPlatform.Mac)
		{
			Platform = "Mac";
			Toolchain = "mac-clang";
		}
		else if (Target.Platform == UnrealTargetPlatform.Linux)
		{
			Platform = "Linux";
			Toolchain = "linux-clang";
		}
		else
		{
			throw new NotImplementedException();
		}

		if (Target.Platform == UnrealTargetPlatform.Win64)
		{
			// Add the import library
			PublicDefinitions.Add("_WIN32");
			PublicDefinitions.Add("_WINSOCK_DEPRECATED_NO_WARNINGS");
		}

		var LibFolder = Path.Combine(ModuleDirectory, Toolchain, "libs", Platform, "x64", "Release");
		string[] Libs = {
			"rd_framework_cpp.lib",
			"rd_framework_cpp_util.lib",
			"clsocket.lib",
			"rd_core_cpp.lib",
			"spdlog.lib"
		};
		foreach (string Lib in Libs)
		{
			PublicAdditionalLibraries.Add(Path.Combine(LibFolder, Lib));
		}

		// Common dependencies
		PublicDefinitions.Add("SPDLOG_COMPILED_LIB");
		PublicDefinitions.Add("nssv_CONFIG_SELECT_STRING_VIEW=nssv_STRING_VIEW_NONSTD");

		string[] Paths = {
			"include",
			"include/rd_core_cpp",
			"include/rd_framework_cpp",
			"include/rd_core_cpp/lifetime",
			"include/rd_core_cpp/reactive",
			"include/rd_core_cpp/std",
			"include/rd_core_cpp/types",
			"include/rd_core_cpp/util",
			"include/rd_core_cpp/reactive/base",
			"include/rd_framework_cpp/base",
			"include/rd_framework_cpp/ext",
			"include/rd_framework_cpp/impl",
			"include/rd_framework_cpp/intern",
			"include/rd_framework_cpp/scheduler",
			"include/rd_framework_cpp/serialization",
			"include/rd_framework_cpp/task",
			"include/rd_framework_cpp/util",
			"include/rd_framework_cpp/wire",
			"include/rd_framework_cpp/scheduler/base",
			"include/thirdparty",
			"include/thirdparty/clsocket",
			"include/thirdparty/mpark",
			"include/thirdparty/nonstd",
			"include/thirdparty/optional",
			"include/thirdparty/tsl",
			"include/thirdparty/clsocket/src",
			"include/thirdparty/optional/tl"
		};

		foreach(var Item in Paths)
		{
			PublicIncludePaths.Add(Path.Combine(ModuleDirectory, Item));
		}
	}
}
