// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#pragma once


//The external headers and defines goes here
#include "IProtocol.h"
#include "RdEditorProtocol/RdEditorModel/RdEditorModel.h"
#include "SimpleScheduler.h"
#include "SingleThreadScheduler.h"
#include "Templates/UniquePtr.h"


class RdConnection
{
public:
	RdConnection();
	~RdConnection();
	/** Handle to the test dll we will load */
	//RdProperty<tl::optional<int>> test_connection{ 0 };
	//RdProperty<tl::optional<std::wstring> > unreal_log{ L"" };
	//RdProperty<tl::optional<bool> > unreal_play{ false };

	void init();
	Jetbrains::EditorPlugin::RdEditorModel unrealToBackendModel;

	rd::LifetimeDefinition lifetimeDef;
	rd::LifetimeDefinition socketLifetimeDef;

	rd::Lifetime lifetime;
	rd::Lifetime socketLifetime;

	rd::SingleThreadScheduler scheduler;

private:
	TUniquePtr<rd::IProtocol> protocol;
};
