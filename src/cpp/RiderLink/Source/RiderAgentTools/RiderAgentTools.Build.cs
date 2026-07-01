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

        // RTTI is a per-platform compromise, because the two toolchains pull in
        // opposite directions:
        //
        //  * Apple/arm64 (Clang) needs RTTI OFF. This module instantiates UE
        //    polymorphic types (TJsonValue* -> FJsonValue, FMemoryArchive/
        //    FBufferReaderBase -> FArchive, URiderAgentBridgeLibrary ->
        //    UBlueprintFunctionLibrary). With RTTI on, Clang emits typeinfo for
        //    those derived types referencing the base classes' typeinfo, but the
        //    engine modules (Core/Json/CoreUObject) are compiled WITHOUT RTTI and
        //    never emit it -> "Undefined symbols ... typeinfo for FJsonValue/
        //    FArchive/UBlueprintFunctionLibrary" at link time (RIDER-139854).
        //
        //  * Windows (MSVC) needs RTTI ON. The bundled rd library's polymorphic
        //    deserialization (Wrapper<T>::dynamic -> std::dynamic_pointer_cast,
        //    reached via RdEditorModel's AbstractPolymorphic signals) does not
        //    compile under /GR-: MSVC reports the dynamic_pointer_cast overload as
        //    a deleted function (C2280). Clang tolerates that same path with
        //    -fno-rtti, which is why only MSVC regressed (RIDER-139853 follow-up).
        //
        // So: OFF on Apple (link fix), ON everywhere else (rd needs it, and no
        // typeinfo link problem exists there).
        bUseRTTI = Target.Platform != UnrealTargetPlatform.Mac
                && Target.Platform != UnrealTargetPlatform.IOS;

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
