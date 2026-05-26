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

        // Unity builds merge sibling .cpp files into one TU; some files in this
        // module use `using namespace JetBrains::EditorPlugin;` inside an
        // anonymous namespace, which (per C++ rules) makes the EP names
        // reachable from the enclosing scope via the anonymous-namespace
        // implicit using-directive. That collides with UE engine headers (e.g.
        // Subsystems/UnrealEditorSubsystem.h) that reference `UClass`
        // unqualified — `JetBrains::EditorPlugin::UClass` (from UE4Library.kt)
        // becomes ambiguous with the engine's global `::UClass`. Disabling
        // unity isolates each .cpp so the leak can't cross files.
        bUseUnity = false;

        bUseRTTI = true;

#if UE_5_2_OR_LATER
        bDisableStaticAnalysis = true;
#endif

        PublicDependencyModuleNames.Add("RD");

        PrivateDependencyModuleNames.AddRange(new[] {
            "AssetRegistry",
            "Core",
            "CoreUObject",
            "EnhancedInput",
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
