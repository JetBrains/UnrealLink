// Copyright 1998-2020 Epic Games, Inc. All Rights Reserved.

#pragma once

#include "base/IProtocol.h"
#include "scheduler/SingleThreadScheduler.h"
#include "RdEditorProtocol/RdEditorModel/RdEditorModel.Generated.h"

#include "Templates/UniquePtr.h"

class RdConnection
{
public:
	RdConnection();
	~RdConnection();

	void Init();
	void Shutdown();

	JetBrains::EditorPlugin::RdEditorModel UnrealToBackendModel;

private:
	TUniquePtr<rd::IProtocol> Protocol;

	rd::LifetimeDefinition SocketLifetimeDef;
	rd::Lifetime SocketLifetime;

public:
	rd::SingleThreadScheduler Scheduler;
};
