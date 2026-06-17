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

        // NOTE: RTTI must stay OFF here. This module instantiates UE polymorphic
        // types (TJsonValue* -> FJsonValue, FMemoryArchive/FBufferReaderBase ->
        // FArchive, URiderAgentBridgeLibrary -> UBlueprintFunctionLibrary). With
        // RTTI on, Clang emits typeinfo for those derived types that references
        // the base classes' typeinfo, but the engine modules (Core/Json/
        // CoreUObject) are compiled WITHOUT RTTI and never emit it -> "Undefined
        // symbols ... typeinfo for FJsonValue/FArchive/UBlueprintFunctionLibrary"
        // at link time (notably on Apple/arm64). This module's own code uses no
        // dynamic_cast/typeid, so RTTI is not needed.
        bUseRTTI = false;

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

        // Editor-only: screenshot/asset tooling (UnrealEd, LevelEditor, AssetTools),
        // Blueprint graph (Kismet, BlueprintGraph), UMG (UMG, UMGEditor),
        // Niagara VFX (Niagara, NiagaraEditor) — used by URiderAgentBridgeLibrary.
        if (Target.bBuildEditor)
        {
            PrivateDependencyModuleNames.AddRange(new[] {
                "AssetTools",
                "BlueprintGraph",
                "EditorScriptingUtilities",
                "Kismet",
                "LevelEditor",
                "Niagara",
                "NiagaraEditor",
                "UMG",
                "UMGEditor",
                "UnrealEd",
            });
        }
    }
}
