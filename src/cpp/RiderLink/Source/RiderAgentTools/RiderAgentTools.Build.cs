using UnrealBuildTool;

public class RiderAgentTools : ModuleRules
{
    public RiderAgentTools(ReadOnlyTargetRules Target) : base(Target)
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

        PublicDependencyModuleNames.Add("RD");

        PrivateDependencyModuleNames.AddRange(new[] {
            "AssetRegistry",
            "Core",
            "CoreUObject",
            "Engine",
            "ImageWrapper",
            "Json",
            "JsonUtilities",
            "PythonScriptPlugin",
            "RenderCore",
            "RiderLink",
            "Slate",
            "SlateCore",
        });

        // Editor-only screenshot helpers: asset thumbnails (UnrealEd),
        // level-editor viewport widget (LevelEditor).
        if (Target.bBuildEditor)
        {
            PrivateDependencyModuleNames.AddRange(new[] {
                "LevelEditor",
                "UnrealEd",
            });
        }
    }
}
