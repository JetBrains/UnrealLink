// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

using System.IO;
using UnrealBuildTool;

public class RiderLink : ModuleRules
{
	public RiderLink(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = ModuleRules.PCHUsageMode.UseExplicitOrSharedPCHs;
#if UE_4_24_OR_LATER
		bUseUnity = true;
#else
		bFasterWithoutUnity = false;
#endif
		
		bUseRTTI = true;

		PublicDependencyModuleNames.Add("Core");
		PublicDependencyModuleNames.Add("RD");
	}
}
