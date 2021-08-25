// Fill out your copyright notice in the Description page of Project Settings.


#include "RiderSourceCodeNavigationHandler.h"
#include "IRiderLink.hpp"

bool FRiderSourceCodeNavigationHandler::CanNavigateToClass(const UClass* InClass)
{
	return IRiderLinkModule::Get().IsConnected();
}

bool FRiderSourceCodeNavigationHandler::NavigateToClass(const UClass* InClass)
{
	TOptional<bool> bCallAsyncAction = IRiderLinkModule::Get().CallAsyncAction(
		[&InClass] (JetBrains::EditorPlugin::RdEditorModel const& RdEditorModel)
	{
		const JetBrains::EditorPlugin::UClassName UClassName = JetBrains::EditorPlugin::UClassName(InClass->GetName());
			rd::RdCall<JetBrains::EditorPlugin::UClassName, bool, rd::Polymorphic<JetBrains::EditorPlugin::UClassName>, rd::Polymorphic<bool>> const& NavigateToClass = RdEditorModel.get_navigateToClass();
			return NavigateToClass.start(UClassName).is_succeeded();
	});
	return bCallAsyncAction.IsSet() && bCallAsyncAction.GetValue();
}

bool FRiderSourceCodeNavigationHandler::CanNavigateToStruct(const UScriptStruct* InStruct)
{
	return false;
}

bool FRiderSourceCodeNavigationHandler::NavigateToStruct(const UScriptStruct* InStruct)
{
	return ISourceCodeNavigationHandler::NavigateToStruct(InStruct);
}

bool FRiderSourceCodeNavigationHandler::CanNavigateToFunction(const UFunction* InFunction)
{
	return IRiderLinkModule::Get().IsConnected();
}

bool FRiderSourceCodeNavigationHandler::NavigateToFunction(const UFunction* InFunction)
{
	TOptional<bool> bCallAsyncAction = IRiderLinkModule::Get().CallAsyncAction(
		[&InFunction] (JetBrains::EditorPlugin::RdEditorModel const& RdEditorModel)
	{
		const UClass* OwningClass = InFunction->GetOwnerClass();
		JetBrains::EditorPlugin::UClassName UClassName = JetBrains::EditorPlugin::UClassName(OwningClass->GetName());
		const JetBrains::EditorPlugin::MethodReference Reference{UClassName, InFunction->GetName() };
		return RdEditorModel.get_navigateToMethod().start(Reference).is_succeeded();
	});
	return bCallAsyncAction.IsSet() && bCallAsyncAction.GetValue();
}

bool FRiderSourceCodeNavigationHandler::CanNavigateToProperty(const UProperty* InProperty)
{
	return false;
}

bool FRiderSourceCodeNavigationHandler::NavigateToProperty(const UProperty* InProperty)
{
	return ISourceCodeNavigationHandler::NavigateToProperty(InProperty);
}

bool FRiderSourceCodeNavigationHandler::CanNavigateToStruct(const UStruct* InStruct)
{
	return false;
}

bool FRiderSourceCodeNavigationHandler::NavigateToStruct(const UStruct* InStruct)
{
	return ISourceCodeNavigationHandler::NavigateToStruct(InStruct);
}
