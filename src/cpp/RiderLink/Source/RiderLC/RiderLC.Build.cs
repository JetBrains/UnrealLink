using UnrealBuildTool;

public class RiderLC : ModuleRules
{
    public RiderLC(ReadOnlyTargetRules Target) : base(Target)
    {
        PCHUsage = ModuleRules.PCHUsageMode.UseExplicitOrSharedPCHs;
        
        bUseRTTI = true;
        PrivateDefinitions.Add("_SILENCE_CXX17_CODECVT_HEADER_DEPRECATION_WARNING");
        PrivateDefinitions.Add("_SILENCE_ALL_CXX17_DEPRECATION_WARNINGS");
        PrivateDependencyModuleNames.AddRange(
            new string[]
            {
                "Core",
                "CoreUObject",
                "Engine",
                "Slate",
                "SlateCore",
                "RiderLink",
                "LiveCoding",
                "RD"
            }
        );
    }
}