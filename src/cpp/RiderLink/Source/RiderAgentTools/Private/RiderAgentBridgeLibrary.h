#pragma once

#include "CoreMinimal.h"
#include "Kismet/BlueprintFunctionLibrary.h"
#include "RiderAgentBridgeLibrary.generated.h"

/**
 * Python-exposed editor helpers that fill gaps in Unreal's Python API.
 * Callable from Rider's ue_execute_python tool as:
 *   unreal.RiderAgentBridgeLibrary.method_name(args)
 * Editor-only. Runs on the game thread (the Python transport dispatches there).
 */
UCLASS()
class URiderAgentBridgeLibrary : public UBlueprintFunctionLibrary
{
    GENERATED_BODY()

public:
    // ── Console Variables ──
    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|CVar")
    static FString ReadCVar(const FString& Name);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|CVar")
    static bool WriteCVar(const FString& Name, const FString& Value);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|CVar")
    static FString GetCVarInfo(const FString& Name);

    // ── Editor Notification ──
    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Notify")
    static void ShowNotification(const FString& Text, const FString& Type = TEXT("info"), float Duration = 0.0f);

    // ── Modal Dialog Suppression ──
    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Dialog")
    static void SetSuppressModalDialogs(bool bSuppress);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Dialog")
    static bool IsSuppressingModalDialogs();

    // ── Asset Operations ──
    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Asset")
    static bool ForceDeleteAsset(const FString& PackagePath);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Asset")
    static int32 ForceDeleteAssets(const TArray<FString>& PackagePaths);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Asset")
    static FString DuplicateAsset(const FString& SourcePath, const FString& DestPath);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Asset")
    static UObject* EnsureAsset(const FString& PackagePath, const FString& AssetName,
        const FString& ClassName, const FString& FactoryClassName);

    // ── Blueprint Graph ──
    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static FString GetAllBlueprintGraphs(const FString& BlueprintPath);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static FString GetBlueprintGraphNodes(const FString& BlueprintPath, const FString& GraphName);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static FString AddBlueprintNode(const FString& BlueprintPath, const FString& GraphName,
        const FString& NodeClassName, const FString& NodeParamsJson, int32 X, int32 Y);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static bool ConnectBlueprintPins(const FString& BlueprintPath, const FString& GraphName,
        const FString& SourceNodeName, const FString& SourcePinName,
        const FString& TargetNodeName, const FString& TargetPinName);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static bool RemoveBlueprintNode(const FString& BlueprintPath, const FString& GraphName,
        const FString& NodeName);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static bool SetPinDefaultValue(const FString& BlueprintPath, const FString& GraphName,
        const FString& NodeName, const FString& PinName, const FString& DefaultValue);

    // ── Blueprint Variables ──
    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static bool AddBlueprintVariable(const FString& BlueprintPath, const FString& VariableName,
        const FString& PinCategoryName, const FString& PinSubCategoryName,
        const FString& PinSubCategoryObject, const FString& ContainerType, bool bIsReference);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static bool RemoveBlueprintVariable(const FString& BlueprintPath, const FString& VariableName);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static bool SetBlueprintVariableCategory(const FString& BlueprintPath, const FString& VariableName,
        const FString& CategoryName);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static bool SetBlueprintVariableDefaultValue(const FString& BlueprintPath, const FString& VariableName,
        const FString& ValueText);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static FString ExportBlueprintNodes(const FString& BlueprintPath, const FString& GraphName,
        const FString& NodeNamesJson);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Blueprint")
    static FString ImportBlueprintNodes(const FString& BlueprintPath, const FString& GraphName,
        const FString& ClipboardText, int32 OffsetX, int32 OffsetY);

    // ── Widget Tree ──
    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Widget")
    static bool AddWidgetToTree(const FString& WidgetBlueprintPath, const FString& ParentWidgetName,
        const FString& ChildWidgetClass, const FString& ChildWidgetName);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Widget")
    static bool RemoveWidgetFromTree(const FString& WidgetBlueprintPath, const FString& WidgetName);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Widget")
    static FString ListWidgetsInTree(const FString& WidgetBlueprintPath);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Widget")
    static bool SetWidgetProperty(const FString& WidgetBlueprintPath, const FString& WidgetName,
        const FString& PropertyName, const FString& ValueText);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Widget")
    static bool SetWidgetSlotProperty(const FString& WidgetBlueprintPath, const FString& WidgetName,
        const FString& PropertyName, const FString& ValueText);

    // ── Niagara ──
    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Niagara")
    static FString GetNiagaraSystemParameters(const FString& NiagaraSystemPath);

    UFUNCTION(BlueprintCallable, Category = "RiderAgentBridge|Niagara")
    static FString GetNiagaraSystemEmitters(const FString& NiagaraSystemPath);
};
