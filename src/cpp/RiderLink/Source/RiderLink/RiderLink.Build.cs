using System.IO;
using UnrealBuildTool;

public class RiderLink : ModuleRules
{
	public RiderLink(ReadOnlyTargetRules Target) : base(Target)
	{
#if UE_4_22_OR_LATER
		PCHUsage = PCHUsageMode.NoPCHs;
#else
		PCHUsage = PCHUsageMode.NoSharedPCHs;
#endif
		
		bUseRTTI = true;

#if UE_5_2_OR_LATER
		bDisableStaticAnalysis = true;
#endif

		PublicDependencyModuleNames.Add("Core");
		PublicDependencyModuleNames.Add("RD");
		string[] Paths = {
			"Public/Model/Library",
			"Public/Model/RdEditorProtocol",
		};
		
		PrivateDefinitions.Add("ENABLE_LOG_FILE=0");

		foreach(var Item in Paths)
		{
			PublicIncludePaths.Add(Path.Combine(ModuleDirectory, Item));
		}
	}
}
