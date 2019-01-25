// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

using System.IO;
using UnrealBuildTool;

public class RiderLink : ModuleRules
{
	public RiderLink(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = ModuleRules.PCHUsageMode.UseExplicitOrSharedPCHs;
		
		PublicIncludePaths.AddRange(
			new string[] {
				// ... add public include paths required here ...
			}
			);
				
		
		PrivateIncludePaths.AddRange(
			new string[] {
			}
			);
			
		
		PublicDependencyModuleNames.AddRange(
			new string[]
			{
				// ... add other public dependencies that you statically link with here ...
			}
			);
			
		
		PrivateDependencyModuleNames.AddRange(
			new string[]
			{
                 "Core"
                ,"SourceCodeAccess"
                ,"DesktopPlatform"
                ,"Json"
				,"UnrealEd"
				// ... add private dependencies that you statically link with here ...	
			}
            );
		
		
		DynamicallyLoadedModuleNames.AddRange(
			new string[]
			{
				// ... add any modules that your module loads dynamically here ...
			}
			);

		bUseRTTI = true;

        if (Target.Platform == UnrealTargetPlatform.Win64)
		{
            // Add the import library
            PublicDefinitions.Add("_WIN32");
            PublicDefinitions.Add("_WINSOCK_DEPRECATED_NO_WARNINGS");

            PublicLibraryPaths.Add(Path.Combine(ModuleDirectory,"Libs", "Win", "x64"));
			PublicAdditionalLibraries.AddRange(new string[] {
				"rd_framework_cpp.lib"
                 ,"clsocket.lib"
                 ,"rd_core_cpp.lib"
            });

            string[] paths = new string[] {
                 "include"
				,"include/clsocket"
				,"include/clsocket/src"
				,"include/mpark"
				,"include/optional"
				,"include/optional/tl"
				,"include/rd_core_cpp"
				,"include/rd_core_cpp/lifetime"
				,"include/rd_core_cpp/reactive"
				,"include/rd_core_cpp/reactive/base"
				,"include/rd_core_cpp/util"
				,"include/rd_core_cpp/util/ordered-map"
				,"include/rd_core_cpp/util/ordered-map/include"
				,"include/rd_core_cpp/util/ordered-map/include/tsl"
				,"include/rd_framework_cpp"
				,"include/rd_framework_cpp/base"
				,"include/rd_framework_cpp/base/ext"
				,"include/rd_framework_cpp/impl"
				,"include/rd_framework_cpp/serialization"
				,"include/rd_framework_cpp/task"
				,"include/rd_framework_cpp/util"
				,"include/rd_framework_cpp/wire"
				,"include/rd_framework_cpp/wire/threading"
            };

			foreach(var item in paths)
            {
                PublicIncludePaths.Add(Path.Combine(ModuleDirectory, item));
            }
		}
	}
}
