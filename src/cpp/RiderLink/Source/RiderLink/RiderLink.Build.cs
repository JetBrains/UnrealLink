// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

using System.IO;
using UnrealBuildTool;

public class RiderLink : ModuleRules
{
	public RiderLink(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = ModuleRules.PCHUsageMode.UseExplicitOrSharedPCHs;
		
		bUseRTTI = true;

		PrivateDependencyModuleNames.AddRange(
			new string[]
			{
                 "Core"
				,"CoreUObject"
				,"DesktopPlatform"
				,"Engine"
				,"Json"
                ,"UnrealEd"
                ,"UnrealEdMessages"
                ,"MessagingCommon"
                ,"AssetRegistry"
                ,"ContentBrowser"
                ,"Slate"
                ,"SlateCore"
				// ... add private dependencies that you statically link with here ...	
			}
        );
		
        if (Target.Platform == UnrealTargetPlatform.Win64)
		{
			PrivateDependencyModuleNames.Add("RD");
		}
	}
}
