#include "BlueprintProvider.h"

#include "UObject/UObjectIterator.h"
#include "UObject/UObjectGlobals.h"
#include "EdGraph/EdGraph.h"
#include "Toolkits/AssetEditorManager.h"
#include "BlueprintEditor.h"
#include "AssetEditorMessages.h"
#include "MessageEndpointBuilder.h"

bool BluePrintProvider::IsBlueprint(FString const& Path, FString const& Name) {
	TArray<UBlueprint*> blueprints;
	for (TObjectIterator<UBlueprint> Itr; Itr; ++Itr) {
		UBlueprint* bp = *Itr;
		if (bp->GeneratedClass) {
			blueprints.Add(bp);
		}
	}
	for (UBlueprint* bp : blueprints) {
		auto pathName = bp->GeneratedClass->GetPathName();
		TArray<UEdGraph*> allGraphs;
		bp->GetAllGraphs(allGraphs);
		// adds event graph, but only the generated intermediate representation, not the start nodes
		// allGraphs.Append(bp->EventGraphs);
		TMap<int32, UEdGraph*> graphIndices;
		for (auto graph : allGraphs) {
			if (pathName == Path && graph->GetName() == Name) {
				return true;
			}
		}
	}
	return false;
}

static void JumpToGraph(IBlueprintEditor& bpEditor, /*UEdGraph* Graph*/UBlueprint *bp) {
	bpEditor.JumpToHyperlink(/*Graph*/bp, false);
}

void BluePrintProvider::OpenBlueprint(FString const& path, FString const& name, TSharedPtr<FMessageEndpoint, ESPMode::ThreadSafe> const& messageEndpoint) {
	messageEndpoint->Publish(new FAssetEditorRequestOpenAsset(path), EMessageScope::Process);
	// FAssetEditorManager::Get().OpenEditorForAsset(path);
	// auto FullPath = path + (name.Len() == 0 ? TEXT(""): TEXT(":") + name);
	// UObject* cls = StaticLoadObject(UObject::StaticClass(), nullptr, *path);
	// UBlueprint* Blueprint = Cast<UBlueprint>(cls);
	//
	// if (Blueprint && Blueprint->IsValidLowLevel()) {
	// 	// check to see if the blueprint is already opened in one of the editors
	// 	auto editors = FAssetEditorManager::Get().FindEditorsForAsset(Blueprint);
	// 	for (IAssetEditorInstance* editor : editors) {
	// 		FBlueprintEditor* bpEditor = static_cast<FBlueprintEditor*>(editor);
	// 		if (bpEditor && bpEditor->GetBlueprintObj() == Blueprint) {
	// 			bpEditor->BringToolkitToFront();			
	// 			/*if (Graph&& Graph->IsValidLowLevel())
	// 			{
	// 				JumpToGraph(*bpEditor, Graph);
	// 			}*/
	// 			JumpToGraph(*bpEditor, Blueprint);
	// 			//NOT TESTED WITH JUMPING TO BLUEPRINT INSTEAD OF GRAPH
	// 		}
	// 		return;
	// 	}
	//
	// 	/*
	// 	// open a new editor
	// 	FBlueprintEditorModule& BlueprintEditorModule =
	// 		FModuleManager::LoadModuleChecked<FBlueprintEditorModule>("Kismet");
	// 	const TSharedRef<IBlueprintEditor> NewKismetEditor = BlueprintEditorModule.CreateBlueprintEditor(
	// 		EToolkitMode::Standalone, TSharedPtr<IToolkitHost>(), Blueprint);
	// 	/*if (Graph&& Graph->IsValidLowLevel())
	// 	{
	// 		JumpToGraph(NewKismetEditor.Get(), Graph);
	// 	}#1#
	// 	*/
	// 	
	// }
	// else {
	// 	// TODO: log that blueprint reference is no longer valid
	// }
}
