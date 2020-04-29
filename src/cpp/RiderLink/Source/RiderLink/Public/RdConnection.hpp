// Copyright 1998-2020 Epic Games, Inc. All Rights Reserved.

#pragma once

#include "rd_framework_cpp/base/IProtocol.h"
#include "rd_framework_cpp/scheduler/SingleThreadScheduler.h"
#include "RdEditorProtocol/RdEditorModel/RdEditorModel.h"

#include "Templates/UniquePtr.h"

class RdConnection
{
public:
	RdConnection();
	~RdConnection();

	void Init();
	void Shutdown();

	Jetbrains::EditorPlugin::RdEditorModel UnrealToBackendModel;

private:
	TUniquePtr<rd::IProtocol> Protocol;

	rd::LifetimeDefinition SocketLifetimeDef;
	rd::Lifetime SocketLifetime;

public:
	rd::SingleThreadScheduler Scheduler;
};
