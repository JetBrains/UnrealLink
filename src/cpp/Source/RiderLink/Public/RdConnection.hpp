// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#pragma once


#include "Windows/AllowWindowsPlatformTypes.h"

//The external headers and defines goes here
#include "RdEditorProtocol/RdEditorModel.h"
#include "Identities.h"
#include "wire/SocketWire.h"
#include "Protocol.h"
#include "RdProperty.h"
#include "PumpScheduler.h"

#include "Windows/HideWindowsPlatformTypes.h"

#include "RdSingleThreadScheduler.hpp"

#include <cstdint>
#include <string>

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
	RdEditorModel unrealToBackendModel;

	LifetimeDefinition lifetimeDef;
	LifetimeDefinition socketLifetimeDef;

	Lifetime lifetime;
	Lifetime socketLifetime;

	RdSingleThreadScheduler clientScheduler;

private:

	std::shared_ptr<IWire> wire;
	std::unique_ptr<Protocol> clientProtocol;
};
