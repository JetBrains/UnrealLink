// Fill out your copyright notice in the Description page of Project Settings.


#include "RiderSourceCodeNavigationHandler.h"

bool FRiderSourceCodeNavigationHandler::CanNavigateToClass(const UClass* InClass)
{
	return ISourceCodeNavigationHandler::CanNavigateToClass(InClass);
}

bool FRiderSourceCodeNavigationHandler::NavigateToClass(const UClass* InClass)
{
	return ISourceCodeNavigationHandler::NavigateToClass(InClass);
}

bool FRiderSourceCodeNavigationHandler::CanNavigateToStruct(const UScriptStruct* InStruct)
{
	return ISourceCodeNavigationHandler::CanNavigateToStruct(InStruct);
}

bool FRiderSourceCodeNavigationHandler::NavigateToStruct(const UScriptStruct* InStruct)
{
	return ISourceCodeNavigationHandler::NavigateToStruct(InStruct);
}

bool FRiderSourceCodeNavigationHandler::CanNavigateToFunction(const UFunction* InFunction)
{
	return ISourceCodeNavigationHandler::CanNavigateToFunction(InFunction);
}

bool FRiderSourceCodeNavigationHandler::NavigateToFunction(const UFunction* InFunction)
{
	return ISourceCodeNavigationHandler::NavigateToFunction(InFunction);
}

bool FRiderSourceCodeNavigationHandler::CanNavigateToProperty(const UProperty* InProperty)
{
	return ISourceCodeNavigationHandler::CanNavigateToProperty(InProperty);
}

bool FRiderSourceCodeNavigationHandler::NavigateToProperty(const UProperty* InProperty)
{
	return ISourceCodeNavigationHandler::NavigateToProperty(InProperty);
}

bool FRiderSourceCodeNavigationHandler::CanNavigateToStruct(const UStruct* InStruct)
{
	return ISourceCodeNavigationHandler::CanNavigateToStruct(InStruct);
}

bool FRiderSourceCodeNavigationHandler::NavigateToStruct(const UStruct* InStruct)
{
	return ISourceCodeNavigationHandler::NavigateToStruct(InStruct);
}
