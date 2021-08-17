// Fill out your copyright notice in the Description page of Project Settings.

#pragma once

#include "CoreMinimal.h"
#include "SourceCodeNavigation.h"

/**
 * 
 */
class RIDERCODENAVIGATIONEXTENSION_API FRiderSourceCodeNavigationHandler : public ISourceCodeNavigationHandler
{
public:
	virtual bool CanNavigateToClass(const UClass* InClass) override;
	virtual bool NavigateToClass(const UClass* InClass) override;
	virtual bool CanNavigateToStruct(const UScriptStruct* InStruct) override;
	virtual bool NavigateToStruct(const UScriptStruct* InStruct) override;
	virtual bool CanNavigateToFunction(const UFunction* InFunction) override;
	virtual bool NavigateToFunction(const UFunction* InFunction) override;
	virtual bool CanNavigateToProperty(const UProperty* InProperty) override;
	virtual bool NavigateToProperty(const UProperty* InProperty) override;
	virtual bool CanNavigateToStruct(const UStruct* InStruct) override;
	virtual bool NavigateToStruct(const UStruct* InStruct) override;
};
