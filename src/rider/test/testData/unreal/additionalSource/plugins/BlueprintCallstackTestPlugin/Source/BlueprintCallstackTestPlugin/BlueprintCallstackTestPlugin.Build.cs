using UnrealBuildTool;

public class BlueprintCallstackTestPlugin : ModuleRules
{
	public BlueprintCallstackTestPlugin(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = ModuleRules.PCHUsageMode.UseExplicitOrSharedPCHs;

		PublicDependencyModuleNames.AddRange(new string[] { "Core", "CoreUObject", "Engine" });

		PrivateDependencyModuleNames.AddRange(new string[] { "BlueprintGraph", "Kismet", "KismetCompiler", "UnrealEd" });
	}
}
