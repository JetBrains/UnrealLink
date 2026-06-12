#include "RiderAgentBridgeLibrary.h"
#include "HAL/IConsoleManager.h"
#include "Misc/CoreMiscDefines.h"
#include "Serialization/JsonWriter.h"
#include "Serialization/JsonSerializer.h"
#include "Serialization/JsonReader.h"
#include "Framework/Notifications/NotificationManager.h"
#include "Widgets/Notifications/SNotificationList.h"
#include "EditorAssetLibrary.h"
#include "ObjectTools.h"
#include "AssetToolsModule.h"
#include "IAssetTools.h"
#include "Factories/Factory.h"
#include "Modules/ModuleManager.h"
#include "Engine/Blueprint.h"
#include "EdGraph/EdGraph.h"
#include "EdGraph/EdGraphPin.h"
#include "K2Node.h"
#include "Kismet2/BlueprintEditorUtils.h"
#include "K2Node_CallFunction.h"
#include "K2Node_CustomEvent.h"
#include "K2Node_VariableGet.h"
#include "K2Node_VariableSet.h"
#include "K2Node_DynamicCast.h"
#include "EdGraphSchema_K2.h"
#include "EdGraphUtilities.h"
#include "WidgetBlueprint.h"
#include "Blueprint/WidgetTree.h"
#include "Components/PanelWidget.h"
#include "Components/TextBlock.h"
#include "Components/Button.h"
#include "Components/Image.h"
#include "Components/ProgressBar.h"
#include "NiagaraSystem.h"
#include "NiagaraEmitterHandle.h"
#include "NiagaraUserRedirectionParameterStore.h"

DEFINE_LOG_CATEGORY_STATIC(LogRiderAgentBridge, Log, All);

FString URiderAgentBridgeLibrary::ReadCVar(const FString& Name)
{
    IConsoleVariable* CVar = IConsoleManager::Get().FindConsoleVariable(*Name);
    if (!CVar) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("CVar '%s' not found"), *Name); return FString(); }
    return CVar->GetString();
}

bool URiderAgentBridgeLibrary::WriteCVar(const FString& Name, const FString& Value)
{
    IConsoleVariable* CVar = IConsoleManager::Get().FindConsoleVariable(*Name);
    if (!CVar) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("CVar '%s' not found"), *Name); return false; }
    if (CVar->TestFlags(ECVF_ReadOnly)) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("CVar '%s' read-only"), *Name); return false; }
    CVar->Set(*Value, ECVF_SetByConsole);
    return true;
}

FString URiderAgentBridgeLibrary::GetCVarInfo(const FString& Name)
{
    IConsoleVariable* CVar = IConsoleManager::Get().FindConsoleVariable(*Name);
    if (!CVar) return TEXT("{}");
    FString Out;
    const TSharedRef<TJsonWriter<>> W = TJsonWriterFactory<>::Create(&Out);
    W->WriteObjectStart();
    W->WriteValue(TEXT("name"), Name);
    W->WriteValue(TEXT("value"), CVar->GetString());
    W->WriteValue(TEXT("help"), CVar->GetHelp());
    const TCHAR* Type = CVar->IsVariableInt() ? TEXT("int")
        : CVar->IsVariableFloat() ? TEXT("float")
        : CVar->IsVariableString() ? TEXT("string") : TEXT("unknown");
    W->WriteValue(TEXT("type"), Type);
    W->WriteValue(TEXT("read_only"), CVar->TestFlags(ECVF_ReadOnly));
    W->WriteValue(TEXT("scalability"), CVar->TestFlags(ECVF_Scalability));
    W->WriteObjectEnd();
    W->Close();
    return Out;
}

void URiderAgentBridgeLibrary::ShowNotification(const FString& Text, const FString& Type, float Duration)
{
    FNotificationInfo Info(FText::FromString(Text));
    Info.bFireAndForget = true;
    Info.ExpireDuration = (Duration > 0.0f) ? Duration : 3.0f;
    SNotificationItem::ECompletionState State = SNotificationItem::CS_None;
    if (Type.Equals(TEXT("success"), ESearchCase::IgnoreCase)) State = SNotificationItem::CS_Success;
    else if (Type.Equals(TEXT("error"), ESearchCase::IgnoreCase) || Type.Equals(TEXT("fail"), ESearchCase::IgnoreCase)) State = SNotificationItem::CS_Fail;
    const TSharedPtr<SNotificationItem> Item = FSlateNotificationManager::Get().AddNotification(Info);
    if (Item.IsValid() && State != SNotificationItem::CS_None) Item->SetCompletionState(State);
}

namespace
{
    bool GRiderBridgeDialogsSuppressed = false;
    bool GRiderBridgePrevUnattended = false;

    UBlueprint* LoadBlueprintFromPath(const FString& Path)
    {
        return Cast<UBlueprint>(UEditorAssetLibrary::LoadAsset(Path));
    }

    UEdGraph* FindGraphInBlueprint(UBlueprint* BP, const FString& GraphName)
    {
        if (!BP) return nullptr;
        TArray<UEdGraph*> All;
        BP->GetAllGraphs(All);
        for (UEdGraph* G : All)
            if (G && G->GetName() == GraphName) return G;
        return nullptr;
    }

    UK2Node* FindNodeByName(UEdGraph* Graph, const FString& NodeName)
    {
        if (!Graph) return nullptr;
        // First pass: exact internal name
        for (UEdGraphNode* N : Graph->Nodes)
            if (N && N->GetName() == NodeName) return Cast<UK2Node>(N);
        // Second pass: case-insensitive node title fallback
        for (UEdGraphNode* N : Graph->Nodes)
            if (N && N->GetNodeTitle(ENodeTitleType::FullTitle).ToString().Equals(NodeName, ESearchCase::IgnoreCase))
                return Cast<UK2Node>(N);
        return nullptr;
    }

    // Configures a newly created UK2Node based on type and JSON params
    void ConfigureK2Node(UK2Node* Node, const TSharedPtr<FJsonObject>& Params)
    {
        if (!Node || !Params.IsValid()) return;

        if (UK2Node_CallFunction* CF = Cast<UK2Node_CallFunction>(Node))
        {
            FString FuncRef;
            if (Params->TryGetStringField(TEXT("FunctionReference"), FuncRef))
            {
                // Expected format: "ClassName::FuncName" or "/Script/Module.ClassName:FuncName"
                FString ClassName, FuncName;
                if (FuncRef.Split(TEXT("::"), &ClassName, &FuncName))
                {
                    UClass* FuncClass = FindObject<UClass>(nullptr, *FString::Printf(TEXT("/Script/Engine.%s"), *ClassName));
                    if (!FuncClass) FuncClass = FindObject<UClass>(nullptr, *ClassName);
                    if (FuncClass)
                    {
                        UFunction* Func = FuncClass->FindFunctionByName(*FuncName);
                        if (Func) CF->SetFromFunction(Func);
                    }
                }
                else if (FuncRef.Contains(TEXT(":")))
                {
                    // "/Script/Module.ClassName:FuncName" format
                    FString ClassPath, FuncN;
                    FuncRef.Split(TEXT(":"), &ClassPath, &FuncN, ESearchCase::IgnoreCase, ESearchDir::FromEnd);
                    UClass* FuncClass = FindObject<UClass>(nullptr, *ClassPath);
                    if (FuncClass)
                    {
                        UFunction* Func = FuncClass->FindFunctionByName(*FuncN);
                        if (Func) CF->SetFromFunction(Func);
                    }
                }
            }
        }
        else if (UK2Node_CustomEvent* CE = Cast<UK2Node_CustomEvent>(Node))
        {
            FString EventName;
            if (Params->TryGetStringField(TEXT("CustomFunctionName"), EventName))
                CE->CustomFunctionName = FName(*EventName);
        }
        else if (UK2Node_VariableGet* VG = Cast<UK2Node_VariableGet>(Node))
        {
            FString VarName;
            if (Params->TryGetStringField(TEXT("VariableName"), VarName))
                VG->VariableReference.SetSelfMember(FName(*VarName));
        }
        else if (UK2Node_VariableSet* VS = Cast<UK2Node_VariableSet>(Node))
        {
            FString VarName;
            if (Params->TryGetStringField(TEXT("VariableName"), VarName))
                VS->VariableReference.SetSelfMember(FName(*VarName));
        }
        else if (UK2Node_DynamicCast* DC = Cast<UK2Node_DynamicCast>(Node))
        {
            FString TargetTypeName;
            if (Params->TryGetStringField(TEXT("TargetType"), TargetTypeName))
            {
                UClass* TargetClass = FindObject<UClass>(nullptr, *FString::Printf(TEXT("/Script/Engine.%s"), *TargetTypeName));
                if (!TargetClass) TargetClass = FindObject<UClass>(nullptr, *TargetTypeName);
                if (TargetClass) DC->TargetType = TargetClass;
            }
        }
    }

    UWidgetBlueprint* LoadWidgetBlueprint(const FString& Path)
    {
        return Cast<UWidgetBlueprint>(UEditorAssetLibrary::LoadAsset(Path));
    }

    UClass* FindWidgetClass(const FString& Name)
    {
        static const TMap<FString, UClass*> Map = {
            { TEXT("TextBlock"),   UTextBlock::StaticClass() },
            { TEXT("Button"),      UButton::StaticClass() },
            { TEXT("Image"),       UImage::StaticClass() },
            { TEXT("ProgressBar"), UProgressBar::StaticClass() },
        };
        if (const UClass* const* Found = Map.Find(Name)) return const_cast<UClass*>(*Found);
        for (TObjectIterator<UClass> It; It; ++It)
            if (It->GetName() == Name && It->IsChildOf(UWidget::StaticClass())) return *It;
        return nullptr;
    }
}

void URiderAgentBridgeLibrary::SetSuppressModalDialogs(bool bSuppress)
{
    GRiderBridgeDialogsSuppressed = bSuppress;
    if (bSuppress)
    {
        GRiderBridgePrevUnattended = GIsRunningUnattendedScript;
        GIsRunningUnattendedScript = true;
    }
    else
    {
        GIsRunningUnattendedScript = GRiderBridgePrevUnattended;
    }
    UE_LOG(LogRiderAgentBridge, Log, TEXT("Modal dialog suppression: %s"), bSuppress ? TEXT("ON") : TEXT("OFF"));
}

bool URiderAgentBridgeLibrary::IsSuppressingModalDialogs() { return GRiderBridgeDialogsSuppressed; }

bool URiderAgentBridgeLibrary::ForceDeleteAsset(const FString& PackagePath)
{
    UObject* Asset = UEditorAssetLibrary::LoadAsset(PackagePath);
    if (!Asset) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("Asset '%s' not found"), *PackagePath); return false; }
    TArray<UObject*> ToDelete{ Asset };
    return ObjectTools::ForceDeleteObjects(ToDelete, /*bShowConfirmation=*/false) > 0;
}

int32 URiderAgentBridgeLibrary::ForceDeleteAssets(const TArray<FString>& PackagePaths)
{
    TArray<UObject*> ToDelete;
    for (const FString& P : PackagePaths)
        if (UObject* A = UEditorAssetLibrary::LoadAsset(P)) ToDelete.Add(A);
    return ToDelete.Num() == 0 ? 0 : ObjectTools::ForceDeleteObjects(ToDelete, false);
}

FString URiderAgentBridgeLibrary::DuplicateAsset(const FString& SourcePath, const FString& DestPath)
{
    if (!UEditorAssetLibrary::DoesAssetExist(SourcePath)) return FString();
    FString DestPkg, DestName;
    DestPath.Split(TEXT("/"), &DestPkg, &DestName, ESearchCase::IgnoreCase, ESearchDir::FromEnd);
    if (DestName.IsEmpty()) { DestName = DestPkg; DestPkg = TEXT("/Game"); }
    IAssetTools& Tools = FModuleManager::LoadModuleChecked<FAssetToolsModule>("AssetTools").Get();
    UObject* New = Tools.DuplicateAsset(DestName, DestPkg, UEditorAssetLibrary::LoadAsset(SourcePath));
    if (!New)
    {
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("DuplicateAsset: IAssetTools returned null for '%s' -> '%s/%s'"), *SourcePath, *DestPkg, *DestName);
        return FString();
    }
    const FString NewPath = New->GetPathName();
    UEditorAssetLibrary::SaveAsset(NewPath, false);
    return NewPath;
}

UObject* URiderAgentBridgeLibrary::EnsureAsset(const FString& PackagePath, const FString& AssetName,
    const FString& ClassName, const FString& FactoryClassName)
{
    // If asset already exists, load and return it
    const FString FullPath = PackagePath / AssetName + TEXT(".") + AssetName;
    if (UEditorAssetLibrary::DoesAssetExist(FullPath))
        return UEditorAssetLibrary::LoadAsset(FullPath);

    // Resolve asset class
    UClass* AssetClass = FindObject<UClass>(nullptr, *FString::Printf(TEXT("/Script/Engine.%s"), *ClassName));
    if (!AssetClass)
        AssetClass = FindObject<UClass>(nullptr, *ClassName);
    if (!AssetClass)
    {
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("EnsureAsset: unknown class '%s'"), *ClassName);
        return nullptr;
    }

    // Resolve or auto-detect factory
    UFactory* Factory = nullptr;
    if (!FactoryClassName.IsEmpty())
    {
        UClass* FactClass = FindObject<UClass>(nullptr, *FString::Printf(TEXT("/Script/UnrealEd.%s"), *FactoryClassName));
        if (!FactClass)
            FactClass = FindObject<UClass>(nullptr, *FactoryClassName);
        if (FactClass)
            Factory = NewObject<UFactory>(GetTransientPackage(), FactClass);
    }
    if (!Factory)
    {
        // Auto-detect for common asset types
        static const TMap<FName, FName> AutoMap = {
            { TEXT("Material"),        TEXT("/Script/UnrealEd.MaterialFactoryNew") },
            { TEXT("DataTable"),       TEXT("/Script/UnrealEd.DataTableFactory") },
            { TEXT("WidgetBlueprint"), TEXT("/Script/UMGEditor.WidgetBlueprintFactory") },
        };
        if (const FName* FactoryPath = AutoMap.Find(FName(*ClassName)))
        {
            if (UClass* FactClass = FindObject<UClass>(nullptr, *FactoryPath->ToString()))
                Factory = NewObject<UFactory>(GetTransientPackage(), FactClass);
        }
    }
    if (!Factory)
    {
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("EnsureAsset: no factory for class '%s'"), *ClassName);
        return nullptr;
    }

    IAssetTools& Tools = FModuleManager::LoadModuleChecked<FAssetToolsModule>("AssetTools").Get();
    UObject* Created = Tools.CreateAsset(AssetName, PackagePath, AssetClass, Factory);
    if (!Created)
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("EnsureAsset: CreateAsset failed for '%s/%s'"), *PackagePath, *AssetName);
    return Created;
}

FString URiderAgentBridgeLibrary::GetAllBlueprintGraphs(const FString& BlueprintPath)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    if (!BP) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("GetAllBlueprintGraphs: BP '%s' not found"), *BlueprintPath); return TEXT("[]"); }

    TArray<UEdGraph*> All;
    BP->GetAllGraphs(All);

    FString Out;
    const TSharedRef<TJsonWriter<>> W = TJsonWriterFactory<>::Create(&Out);
    W->WriteArrayStart();
    TSet<UEdGraph*> Seen;
    for (UEdGraph* G : All)
    {
        if (!G || Seen.Contains(G)) continue;
        Seen.Add(G);
        const TCHAR* Type =
            BP->UbergraphPages.Contains(G)           ? TEXT("ubergraph") :
            BP->FunctionGraphs.Contains(G)           ? TEXT("function")  :
            BP->MacroGraphs.Contains(G)              ? TEXT("macro")     :
            BP->DelegateSignatureGraphs.Contains(G)  ? TEXT("delegate")  :
                                                       TEXT("other");
        W->WriteObjectStart();
        W->WriteValue(TEXT("name"), G->GetName());
        W->WriteValue(TEXT("type"), FString(Type));
        W->WriteValue(TEXT("num_nodes"), G->Nodes.Num());
        W->WriteObjectEnd();
    }
    W->WriteArrayEnd();
    W->Close();
    return Out;
}

FString URiderAgentBridgeLibrary::GetBlueprintGraphNodes(const FString& BlueprintPath, const FString& GraphName)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    UEdGraph* Graph = FindGraphInBlueprint(BP, GraphName);
    if (!Graph) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("GetBlueprintGraphNodes: graph '%s' not found in '%s'"), *GraphName, *BlueprintPath); return TEXT("[]"); }

    FString Out;
    const TSharedRef<TJsonWriter<>> W = TJsonWriterFactory<>::Create(&Out);
    W->WriteArrayStart();
    for (UEdGraphNode* N : Graph->Nodes)
    {
        if (!N) continue;
        W->WriteObjectStart();
        W->WriteValue(TEXT("name"), N->GetName());
        W->WriteValue(TEXT("class"), N->GetClass()->GetName());
        W->WriteValue(TEXT("title"), N->GetNodeTitle(ENodeTitleType::FullTitle).ToString());
        W->WriteArrayStart(TEXT("pins"));
        for (UEdGraphPin* P : N->Pins)
        {
            if (!P) continue;
            W->WriteObjectStart();
            W->WriteValue(TEXT("name"), P->GetName());
            W->WriteValue(TEXT("direction"), P->Direction == EGPD_Output ? TEXT("output") : TEXT("input"));
            W->WriteValue(TEXT("type"), P->PinType.PinCategory.ToString());
            W->WriteValue(TEXT("connected"), P->LinkedTo.Num() > 0);
            W->WriteObjectEnd();
        }
        W->WriteArrayEnd();
        W->WriteObjectEnd();
    }
    W->WriteArrayEnd();
    W->Close();
    return Out;
}

FString URiderAgentBridgeLibrary::AddBlueprintNode(const FString& BlueprintPath, const FString& GraphName,
    const FString& NodeClassName, const FString& NodeParamsJson, int32 X, int32 Y)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    if (!BP) return FString();
    UEdGraph* Graph = FindGraphInBlueprint(BP, GraphName);
    if (!Graph) return FString();

    UClass* NodeClass = FindObject<UClass>(nullptr, *FString::Printf(TEXT("/Script/BlueprintGraph.%s"), *NodeClassName));
    if (!NodeClass) NodeClass = FindObject<UClass>(nullptr, *FString::Printf(TEXT("/Script/Engine.%s"), *NodeClassName));
    if (!NodeClass || !NodeClass->IsChildOf(UK2Node::StaticClass())) return FString();

    TSharedPtr<FJsonObject> Params;
    if (!NodeParamsJson.IsEmpty())
        FJsonSerializer::Deserialize(TJsonReaderFactory<>::Create(NodeParamsJson), Params);

    UK2Node* Node = NewObject<UK2Node>(Graph, NodeClass, NAME_None, RF_Transactional);
    if (!Node) return FString();
    Node->NodePosX = X;
    Node->NodePosY = Y;
    ConfigureK2Node(Node, Params);
    Node->CreateNewGuid();
    Node->PostPlacedNewNode();
    Node->AllocateDefaultPins();
    Graph->AddNode(Node, false, false);
    FBlueprintEditorUtils::MarkBlueprintAsStructurallyModified(BP);
    return Node->GetName();
}

bool URiderAgentBridgeLibrary::ConnectBlueprintPins(const FString& BlueprintPath, const FString& GraphName,
    const FString& SourceNodeName, const FString& SourcePinName,
    const FString& TargetNodeName, const FString& TargetPinName)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    if (!BP) return false;
    UEdGraph* Graph = FindGraphInBlueprint(BP, GraphName);
    if (!Graph) return false;
    UK2Node* Src = FindNodeByName(Graph, SourceNodeName);
    UK2Node* Dst = FindNodeByName(Graph, TargetNodeName);
    if (!Src || !Dst) return false;

    auto FindPin = [](UK2Node* N, const FString& Name) -> UEdGraphPin*
    {
        for (UEdGraphPin* P : N->Pins) if (P->GetName() == Name) return P;
        for (UEdGraphPin* P : N->Pins) if (P->GetDisplayName().ToString().Equals(Name, ESearchCase::IgnoreCase)) return P;
        return nullptr;
    };
    UEdGraphPin* SP = FindPin(Src, SourcePinName);
    UEdGraphPin* TP = FindPin(Dst, TargetPinName);
    if (!SP || !TP) return false;

    const UEdGraphSchema* Schema = Graph->GetSchema();
    if (!Schema || Schema->CanCreateConnection(SP, TP).Response == CONNECT_RESPONSE_DISALLOW) return false;
    const bool bOk = Schema->TryCreateConnection(SP, TP);
    if (bOk)
    {
        FBlueprintEditorUtils::MarkBlueprintAsStructurallyModified(BP);
    }
    else
    {
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("ConnectBlueprintPins: TryCreateConnection failed '%s'.%s -> '%s'.%s"),
            *SourceNodeName, *SourcePinName, *TargetNodeName, *TargetPinName);
    }
    return bOk;
}

bool URiderAgentBridgeLibrary::RemoveBlueprintNode(const FString& BlueprintPath, const FString& GraphName, const FString& NodeName)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    UEdGraph* Graph = BP ? FindGraphInBlueprint(BP, GraphName) : nullptr;
    UK2Node* Node = FindNodeByName(Graph, NodeName);
    if (!Node) return false;
    FBlueprintEditorUtils::RemoveNode(BP, Node);
    return true;
}

bool URiderAgentBridgeLibrary::SetPinDefaultValue(const FString& BlueprintPath, const FString& GraphName,
    const FString& NodeName, const FString& PinName, const FString& DefaultValue)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    UEdGraph* Graph = BP ? FindGraphInBlueprint(BP, GraphName) : nullptr;
    UK2Node* Node = FindNodeByName(Graph, NodeName);
    if (!Node) return false;
    for (UEdGraphPin* P : Node->Pins)
        if (P->GetName() == PinName && P->Direction == EGPD_Input)
        {
            const UEdGraphSchema* PinSchema = Node->GetSchema();
            if (!PinSchema) return false;
            PinSchema->TrySetDefaultValue(*P, DefaultValue);
            FBlueprintEditorUtils::MarkBlueprintAsModified(BP);
            return true;
        }
    return false;
}

bool URiderAgentBridgeLibrary::AddBlueprintVariable(const FString& BlueprintPath, const FString& VariableName,
    const FString& PinCategoryName, const FString& PinSubCategoryName,
    const FString& PinSubCategoryObject, const FString& ContainerType, bool bIsReference)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    if (!BP) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("AddBlueprintVariable: BP '%s' not found"), *BlueprintPath); return false; }

    FEdGraphPinType PinType;
    PinType.PinCategory = FName(*PinCategoryName);
    PinType.PinSubCategory = FName(*PinSubCategoryName);
    if (!PinSubCategoryObject.IsEmpty())
        PinType.PinSubCategoryObject = FindObject<UObject>(nullptr, *PinSubCategoryObject);

    if (ContainerType.Equals(TEXT("Array"), ESearchCase::IgnoreCase))
        PinType.ContainerType = EPinContainerType::Array;
    else if (ContainerType.Equals(TEXT("Set"), ESearchCase::IgnoreCase))
        PinType.ContainerType = EPinContainerType::Set;
    else if (ContainerType.Equals(TEXT("Map"), ESearchCase::IgnoreCase))
        PinType.ContainerType = EPinContainerType::Map;
    else
        PinType.ContainerType = EPinContainerType::None;

    PinType.bIsReference = bIsReference;

    if (!FBlueprintEditorUtils::AddMemberVariable(BP, FName(*VariableName), PinType))
    {
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("AddBlueprintVariable: AddMemberVariable failed for '%s'"), *VariableName);
        return false;
    }
    return true;
}

bool URiderAgentBridgeLibrary::RemoveBlueprintVariable(const FString& BlueprintPath, const FString& VariableName)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    if (!BP) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("RemoveBlueprintVariable: BP '%s' not found"), *BlueprintPath); return false; }
    FBlueprintEditorUtils::RemoveMemberVariable(BP, FName(*VariableName));
    return true;
}

bool URiderAgentBridgeLibrary::SetBlueprintVariableCategory(const FString& BlueprintPath, const FString& VariableName,
    const FString& CategoryName)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    if (!BP) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetBlueprintVariableCategory: BP '%s' not found"), *BlueprintPath); return false; }
    FBlueprintEditorUtils::SetBlueprintVariableCategory(BP, FName(*VariableName), nullptr, FText::FromString(CategoryName));
    return true;
}

bool URiderAgentBridgeLibrary::SetBlueprintVariableDefaultValue(const FString& BlueprintPath, const FString& VariableName,
    const FString& ValueText)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    if (!BP) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetBlueprintVariableDefaultValue: BP '%s' not found"), *BlueprintPath); return false; }

    UClass* GeneratedClass = BP->GeneratedClass;
    if (!GeneratedClass) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetBlueprintVariableDefaultValue: no GeneratedClass for '%s'"), *BlueprintPath); return false; }

    UObject* CDO = GeneratedClass->GetDefaultObject();
    if (!CDO) return false;

    FProperty* Prop = GeneratedClass->FindPropertyByName(FName(*VariableName));
    if (!Prop) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetBlueprintVariableDefaultValue: property '%s' not found"), *VariableName); return false; }

    void* PropData = Prop->ContainerPtrToValuePtr<void>(CDO);
    if (!Prop->ImportText_Direct(*ValueText, PropData, CDO, PPF_None))
    {
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetBlueprintVariableDefaultValue: failed to import value '%s' into property '%s'"), *ValueText, *VariableName);
        return false;
    }
    FBlueprintEditorUtils::MarkBlueprintAsModified(BP);
    return true;
}

FString URiderAgentBridgeLibrary::ExportBlueprintNodes(const FString& BlueprintPath, const FString& GraphName,
    const FString& NodeNamesJson)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    UEdGraph* Graph = FindGraphInBlueprint(BP, GraphName);
    if (!Graph) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("ExportBlueprintNodes: graph '%s' not found"), *GraphName); return FString(); }

    TSet<UEdGraphNode*> Selected;
    if (NodeNamesJson.IsEmpty() || NodeNamesJson == TEXT("[]"))
    {
        for (UEdGraphNode* N : Graph->Nodes) if (N) Selected.Add(N);
    }
    else
    {
        TArray<TSharedPtr<FJsonValue>> NamesArr;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(NodeNamesJson);
        if (FJsonSerializer::Deserialize(Reader, NamesArr))
        {
            TMap<FString, UEdGraphNode*> NodeByName;
            for (UEdGraphNode* N : Graph->Nodes)
                if (N) NodeByName.Add(N->GetName(), N);
            for (const TSharedPtr<FJsonValue>& Val : NamesArr)
                if (UEdGraphNode** Found = NodeByName.Find(Val->AsString()))
                    Selected.Add(*Found);
        }
    }

    TSet<UObject*> SelectedObjects;
    for (UEdGraphNode* Node : Selected) SelectedObjects.Add(Node);
    FString ClipboardText;
    FEdGraphUtilities::ExportNodesToText(SelectedObjects, ClipboardText);
    return ClipboardText;
}

FString URiderAgentBridgeLibrary::ImportBlueprintNodes(const FString& BlueprintPath, const FString& GraphName,
    const FString& ClipboardText, int32 OffsetX, int32 OffsetY)
{
    UBlueprint* BP = LoadBlueprintFromPath(BlueprintPath);
    UEdGraph* Graph = FindGraphInBlueprint(BP, GraphName);
    if (!Graph) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("ImportBlueprintNodes: graph '%s' not found"), *GraphName); return TEXT("[]"); }

    TSet<UEdGraphNode*> Imported;
    FEdGraphUtilities::ImportNodesFromText(Graph, ClipboardText, Imported);

    for (UEdGraphNode* N : Imported)
    {
        if (!N) continue;
        N->NodePosX += OffsetX;
        N->NodePosY += OffsetY;
    }

    if (!Imported.IsEmpty())
        FBlueprintEditorUtils::MarkBlueprintAsStructurallyModified(BP);

    FString Out;
    const TSharedRef<TJsonWriter<>> W = TJsonWriterFactory<>::Create(&Out);
    W->WriteArrayStart();
    for (UEdGraphNode* N : Imported)
        if (N) W->WriteValue(N->GetName());
    W->WriteArrayEnd();
    W->Close();
    return Out;
}

bool URiderAgentBridgeLibrary::AddWidgetToTree(const FString& WidgetBlueprintPath, const FString& ParentWidgetName,
    const FString& ChildWidgetClass, const FString& ChildWidgetName)
{
    UWidgetBlueprint* WBP = LoadWidgetBlueprint(WidgetBlueprintPath);
    if (!WBP || !WBP->WidgetTree) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("AddWidgetToTree: WBP '%s' not found"), *WidgetBlueprintPath); return false; }

    UWidget* ParentWidget = WBP->WidgetTree->FindWidget(FName(*ParentWidgetName));
    UPanelWidget* Panel = Cast<UPanelWidget>(ParentWidget);
    if (!Panel) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("AddWidgetToTree: parent '%s' not found or not a panel"), *ParentWidgetName); return false; }

    UClass* ChildClass = FindWidgetClass(ChildWidgetClass);
    if (!ChildClass) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("AddWidgetToTree: widget class '%s' not found"), *ChildWidgetClass); return false; }

    UWidget* NewWidget = WBP->WidgetTree->ConstructWidget<UWidget>(ChildClass, FName(*ChildWidgetName));
    if (!NewWidget) return false;
    Panel->AddChild(NewWidget);
    FBlueprintEditorUtils::MarkBlueprintAsStructurallyModified(WBP);
    return true;
}

bool URiderAgentBridgeLibrary::RemoveWidgetFromTree(const FString& WidgetBlueprintPath, const FString& WidgetName)
{
    UWidgetBlueprint* WBP = LoadWidgetBlueprint(WidgetBlueprintPath);
    if (!WBP || !WBP->WidgetTree) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("RemoveWidgetFromTree: WBP '%s' not found"), *WidgetBlueprintPath); return false; }

    UWidget* Widget = WBP->WidgetTree->FindWidget(FName(*WidgetName));
    if (!Widget) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("RemoveWidgetFromTree: widget '%s' not found"), *WidgetName); return false; }
    WBP->WidgetTree->RemoveWidget(Widget);
    if (WBP->WidgetTree->RootWidget == Widget)
    {
        WBP->WidgetTree->RootWidget = nullptr;
    }
    FBlueprintEditorUtils::MarkBlueprintAsStructurallyModified(WBP);
    return true;
}

FString URiderAgentBridgeLibrary::ListWidgetsInTree(const FString& WidgetBlueprintPath)
{
    UWidgetBlueprint* WBP = LoadWidgetBlueprint(WidgetBlueprintPath);
    if (!WBP || !WBP->WidgetTree) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("ListWidgetsInTree: WBP '%s' not found"), *WidgetBlueprintPath); return TEXT("[]"); }

    FString Out;
    const TSharedRef<TJsonWriter<>> W = TJsonWriterFactory<>::Create(&Out);
    W->WriteArrayStart();
    WBP->WidgetTree->ForEachWidget([&](UWidget* Widget)
    {
        if (!Widget) return;
        W->WriteObjectStart();
        W->WriteValue(TEXT("name"), Widget->GetName());
        W->WriteValue(TEXT("class"), Widget->GetClass()->GetName());
        const FString ParentName = Widget->GetParent() ? Widget->GetParent()->GetName() : FString();
        W->WriteValue(TEXT("parent"), ParentName);
        const FString SlotName = Widget->Slot ? Widget->Slot->GetClass()->GetName() : FString();
        W->WriteValue(TEXT("slot"), SlotName);
        W->WriteObjectEnd();
    });
    W->WriteArrayEnd();
    W->Close();
    return Out;
}

bool URiderAgentBridgeLibrary::SetWidgetProperty(const FString& WidgetBlueprintPath, const FString& WidgetName,
    const FString& PropertyName, const FString& ValueText)
{
    UWidgetBlueprint* WBP = LoadWidgetBlueprint(WidgetBlueprintPath);
    if (!WBP || !WBP->WidgetTree) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetWidgetProperty: WBP '%s' not found"), *WidgetBlueprintPath); return false; }

    UWidget* Widget = WBP->WidgetTree->FindWidget(FName(*WidgetName));
    if (!Widget) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetWidgetProperty: widget '%s' not found"), *WidgetName); return false; }

    FProperty* Prop = Widget->GetClass()->FindPropertyByName(FName(*PropertyName));
    if (!Prop) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetWidgetProperty: property '%s' not found on '%s'"), *PropertyName, *WidgetName); return false; }

    void* PropData = Prop->ContainerPtrToValuePtr<void>(Widget);
    if (!Prop->ImportText_Direct(*ValueText, PropData, Widget, PPF_None))
    {
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetWidgetProperty: failed to import value '%s' into property '%s' on '%s'"), *ValueText, *PropertyName, *WidgetName);
        return false;
    }
    FBlueprintEditorUtils::MarkBlueprintAsModified(WBP);
    return true;
}

bool URiderAgentBridgeLibrary::SetWidgetSlotProperty(const FString& WidgetBlueprintPath, const FString& WidgetName,
    const FString& PropertyName, const FString& ValueText)
{
    UWidgetBlueprint* WBP = LoadWidgetBlueprint(WidgetBlueprintPath);
    if (!WBP || !WBP->WidgetTree) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetWidgetSlotProperty: WBP '%s' not found"), *WidgetBlueprintPath); return false; }

    UWidget* Widget = WBP->WidgetTree->FindWidget(FName(*WidgetName));
    if (!Widget || !Widget->Slot) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetWidgetSlotProperty: widget '%s' or slot not found"), *WidgetName); return false; }

    FProperty* Prop = Widget->Slot->GetClass()->FindPropertyByName(FName(*PropertyName));
    if (!Prop) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetWidgetSlotProperty: slot property '%s' not found"), *PropertyName); return false; }

    void* PropData = Prop->ContainerPtrToValuePtr<void>(Widget->Slot);
    if (!Prop->ImportText_Direct(*ValueText, PropData, Widget->Slot, PPF_None))
    {
        UE_LOG(LogRiderAgentBridge, Warning, TEXT("SetWidgetSlotProperty: failed to import value '%s' into slot property '%s' on '%s'"), *ValueText, *PropertyName, *WidgetName);
        return false;
    }
    FBlueprintEditorUtils::MarkBlueprintAsModified(WBP);
    return true;
}

FString URiderAgentBridgeLibrary::GetNiagaraSystemParameters(const FString& NiagaraSystemPath)
{
    UNiagaraSystem* System = Cast<UNiagaraSystem>(UEditorAssetLibrary::LoadAsset(NiagaraSystemPath));
    if (!System) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("GetNiagaraSystemParameters: system '%s' not found"), *NiagaraSystemPath); return TEXT("[]"); }

    const FNiagaraUserRedirectionParameterStore& Store = System->GetExposedParameters();
    TArray<FNiagaraVariable> Variables;
    Store.GetParameters(Variables);

    FString Out;
    const TSharedRef<TJsonWriter<>> W = TJsonWriterFactory<>::Create(&Out);
    W->WriteArrayStart();
    for (const FNiagaraVariable& Var : Variables)
    {
        W->WriteObjectStart();
        W->WriteValue(TEXT("name"), Var.GetName().ToString());
        W->WriteValue(TEXT("type"), Var.GetType().GetName());
        W->WriteObjectEnd();
    }
    W->WriteArrayEnd();
    W->Close();
    return Out;
}

FString URiderAgentBridgeLibrary::GetNiagaraSystemEmitters(const FString& NiagaraSystemPath)
{
    UNiagaraSystem* System = Cast<UNiagaraSystem>(UEditorAssetLibrary::LoadAsset(NiagaraSystemPath));
    if (!System) { UE_LOG(LogRiderAgentBridge, Warning, TEXT("GetNiagaraSystemEmitters: system '%s' not found"), *NiagaraSystemPath); return TEXT("[]"); }

    FString Out;
    const TSharedRef<TJsonWriter<>> W = TJsonWriterFactory<>::Create(&Out);
    W->WriteArrayStart();
    for (const FNiagaraEmitterHandle& Handle : System->GetEmitterHandles())
    {
        W->WriteObjectStart();
        W->WriteValue(TEXT("name"), Handle.GetName().ToString());
        W->WriteValue(TEXT("enabled"), Handle.GetIsEnabled());
        W->WriteObjectEnd();
    }
    W->WriteArrayEnd();
    W->Close();
    return Out;
}
