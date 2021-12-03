// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

using UnrealBuildTool;

public class RiderShaderInfoDump : ModuleRules
{
	public RiderShaderInfoDump(ReadOnlyTargetRules Target) : base(Target)
	{
		PrivateDependencyModuleNames.AddRange(new string[] { "Core",  "Projects", "RenderCore" });
	}
}
