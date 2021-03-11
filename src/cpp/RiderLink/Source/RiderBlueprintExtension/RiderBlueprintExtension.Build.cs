// Copyright 1998-2020 Epic Games, Inc. All Rights Reserved.

using UnrealBuildTool;

public class RiderBlueprintExtension : ModuleRules
{
	public RiderBlueprintExtension(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = ModuleRules.PCHUsageMode.UseExplicitOrSharedPCHs;
		
		bUseRTTI = true;
		
		PublicDependencyModuleNames.Add("RD");

		PrivateDependencyModuleNames.AddRange(new []
		{
			"Core",
			"SlateCore",
			"RiderLink",
			"Slate",
			"AssetRegistry",
			"MessagingCommon",
			"UnrealEd",
			"UnrealEdMessages",
			"Engine",
			"CoreUObject"
		});
	}
}
