// Fill out your copyright notice in the Description page of Project Settings.

#include "EmptyUProject.h"
#include "Modules/ModuleManager.h"

#include "UObject/Object.h"
#include "GameFramework/Actor.h"
#include "Components/ActorComponent.h"
#include "GameFramework/Character.h"
#include "UObject/Interface.h"
#include "GameFramework/Pawn.h"
#include "Widgets/SCompoundWidget.h"
#include "Styling/SlateWidgetStyle.h"
#include "Styling/SlateWidgetStyleContainerBase.h"
#include "Sound/SoundEffectSource.h"
#include "Sound/SoundEffectSubmix.h"
#include "Components/SynthComponent.h"

IMPLEMENT_PRIMARY_GAME_MODULE( FDefaultGameModuleImpl, EmptyUProject, "EmptyUProject" );
