// Copyright Epic Games, Inc. All Rights Reserved.

#include "TestPuzzleProjectGameMode.h"
#include "TestPuzzleProjectPlayerController.h"
#include "TestPuzzleProjectPawn.h"

ATestPuzzleProjectGameMode::ATestPuzzleProjectGameMode()
{
	// no pawn by default
	DefaultPawnClass = ATestPuzzleProjectPawn::StaticClass();
	// use our own player controller class
	PlayerControllerClass = ATestPuzzleProjectPlayerController::StaticClass();
}
