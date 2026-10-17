#include "BlueprintCallstackTestPlugin.h"

#include "BlueprintCallstackTestLibrary.h"
#include "EdGraph/EdGraph.h"
#include "EdGraphSchema_K2.h"
#include "Engine/Blueprint.h"
#include "Engine/BlueprintGeneratedClass.h"
#include "K2Node_CallFunction.h"
#include "K2Node_FunctionEntry.h"
#include "Kismet2/BlueprintEditorUtils.h"
#include "Kismet2/KismetEditorUtilities.h"
#include "Misc/CoreDelegates.h"
#include "UObject/Package.h"

#define LOCTEXT_NAMESPACE "FBlueprintCallstackTestPluginModule"

static const FName GBlueprintFunctionName(TEXT("CallNativeFunction"));

void FBlueprintCallstackTestPluginModule::StartupModule()
{
	PostEngineInitHandle = FCoreDelegates::OnPostEngineInit.AddRaw(this, &FBlueprintCallstackTestPluginModule::RunGeneratedBlueprint);
}

void FBlueprintCallstackTestPluginModule::ShutdownModule()
{
	FCoreDelegates::OnPostEngineInit.Remove(PostEngineInitHandle);
}

void FBlueprintCallstackTestPluginModule::RunGeneratedBlueprint()
{
	UPackage* Package = CreatePackage(TEXT("/Temp/BP_BlueprintCallstackTest"));
	UBlueprint* Blueprint = FKismetEditorUtilities::CreateBlueprint(
		UBlueprintCallstackTestObject::StaticClass(), Package, TEXT("BP_BlueprintCallstackTest"), BPTYPE_Normal,
		UBlueprint::StaticClass(), UBlueprintGeneratedClass::StaticClass());

	UEdGraph* Graph = FBlueprintEditorUtils::CreateNewGraph(
		Blueprint, GBlueprintFunctionName, UEdGraph::StaticClass(), UEdGraphSchema_K2::StaticClass());
	FBlueprintEditorUtils::AddFunctionGraph<UClass>(Blueprint, Graph, /*bIsUserCreated*/ true, nullptr);

	TArray<UK2Node_FunctionEntry*> EntryNodes;
	Graph->GetNodesOfClass(EntryNodes);
	check(EntryNodes.Num() == 1);

	FGraphNodeCreator<UK2Node_CallFunction> NodeCreator(*Graph);
	UK2Node_CallFunction* CallNode = NodeCreator.CreateNode();
	CallNode->SetFromFunction(UBlueprintCallstackTestLibrary::StaticClass()->FindFunctionByName(
		GET_FUNCTION_NAME_CHECKED(UBlueprintCallstackTestLibrary, CalledFromBlueprint)));
	NodeCreator.Finalize();

	const bool bConnected = GetDefault<UEdGraphSchema_K2>()->TryCreateConnection(
		EntryNodes[0]->FindPinChecked(UEdGraphSchema_K2::PN_Then), CallNode->GetExecPin());
	check(bConnected);

	FKismetEditorUtilities::CompileBlueprint(Blueprint);

	UObject* Instance = NewObject<UObject>(GetTransientPackage(), Blueprint->GeneratedClass, TEXT("BlueprintCallstackTestInstance"));
	Instance->ProcessEvent(Instance->FindFunctionChecked(GBlueprintFunctionName), nullptr);
}

#undef LOCTEXT_NAMESPACE

IMPLEMENT_MODULE(FBlueprintCallstackTestPluginModule, BlueprintCallstackTestPlugin)
