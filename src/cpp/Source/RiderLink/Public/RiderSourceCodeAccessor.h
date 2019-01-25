// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "ISourceCodeAccessor.h"

class FRiderSourceCodeAccessor : public ISourceCodeAccessor
{
public:
	/** ISourceCodeAccessor implementation */
	virtual void RefreshAvailability() override;

	virtual bool CanAccessSourceCode() const override;

	virtual bool DoesSolutionExist() const override;

	virtual FName GetFName() const override;

	virtual FText GetNameText() const override;

	virtual FText GetDescriptionText() const override;

	virtual bool OpenSolution() override;

	virtual bool OpenSolutionAtPath(const FString& InSolutionPath) override;

	virtual bool OpenFileAtLine(const FString& FullPath, int32 LineNumber, int32 ColumnNumber = 0) override;

	virtual bool OpenSourceFiles(const TArray<FString>& AbsoluteSourcePaths) override;

	virtual bool AddSourceFiles(const TArray<FString>& AbsoluteSourcePaths, const TArray<FString>& AvailableModules) override;

	virtual bool SaveAllOpenDocuments() const override;

	/**
	 * Frame Tick (Not Used)
	 * @param DeltaTime of frame
	 */
	virtual void Tick(const float DeltaTime) override
	{
	};


private:

	/**
	 * Is Rider installed on this system?
	 */
	bool bHasRiderInstalled = false;
	/**
	 * The path to the Rider executable.
	 */
	FString ExecutablePath;

	/**
	 * Search for Rider installation
	 * @return Executable path
	 */
	FString FindExecutablePath();

	/** String storing the solution path obtained from the module manager to avoid having to use it on a thread */
	mutable FString CachedSolutionPath;

	/** Critical section for updating SolutionPath */
	mutable FCriticalSection CachedSolutionPathCriticalSection;

	/** Accessor for SolutionPath. Will try to update it when called from the game thread, otherwise will use the cached value */
	FString GetSolutionPath() const;
};