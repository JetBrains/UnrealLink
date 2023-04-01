using UnrealBuildTool;

public class RiderLogging : ModuleRules
{
	public RiderLogging(ReadOnlyTargetRules Target) : base(Target)
	{
#if UE_4_22_OR_LATER
		PCHUsage = PCHUsageMode.NoPCHs;
#else
		PCHUsage = PCHUsageMode.NoSharedPCHs;
#endif

#if UE_5_2_OR_LATER
		if(Target.Platform == UnrealTargetPlatform.Linux || Target.Platform == UnrealTargetPlatform.Mac)
		{
			bUseRTTI = false;
		}
		else
		{
			bUseRTTI = true;
		}
#endif
        bUseRTTI = true;
        

		PrivateDependencyModuleNames.AddRange(new []
		{
			"Core",
			"RD",
			"RiderLink",
			"RiderBlueprint"
		});
	}
}