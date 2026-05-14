using UnrealBuildTool;

public class RiderAgentTools : ModuleRules
{
    public RiderAgentTools(ReadOnlyTargetRules Target) : base(Target)
    {
        PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;

        PublicDependencyModuleNames.AddRange(new string[] { "Core" });

        PrivateDependencyModuleNames.AddRange(new string[] {
            "CoreUObject",
            "Engine",
            "Json",
            "JsonUtilities",
            "PythonScriptPlugin",
            "RiderLink",
        });
    }
}
