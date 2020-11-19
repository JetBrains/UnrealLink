// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

using System.IO;
using UnrealBuildTool;

public class RiderLink : ModuleRules
{
	public RiderLink(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;
		
		bUseRTTI = true;

		PublicDependencyModuleNames.Add("Core");
		PublicDependencyModuleNames.Add("RD");
		string[] Paths = {
			"Public/Model",
			"Public/Model/Library/UE4Library"
		};
		
		foreach(var Item in Paths)
		{
			PublicIncludePaths.Add(Path.Combine(ModuleDirectory, Item));
		}
	}
}
