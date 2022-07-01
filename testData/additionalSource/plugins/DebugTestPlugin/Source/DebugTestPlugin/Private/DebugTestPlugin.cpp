// Copyright Epic Games, Inc. All Rights Reserved.

#include "DebugTestPlugin.h"

#include <iostream>

#define LOCTEXT_NAMESPACE "FDebugTestPluginModule"

int Foo(int f)
{
	int fooNum = f + 1;
	return fooNum * 2;
}

int Bar(int b)
{
	return b * 3;
}

int Moo(int m)
{
	return Foo(m) * 4;
}

void FDebugTestPluginModule::StartupModule()
{
	int someNumber = 0;
	someNumber = Foo(someNumber);
	someNumber = Bar(someNumber);
	someNumber = Moo(someNumber);
	std::cout << someNumber;
}

void FDebugTestPluginModule::ShutdownModule()
{
}

#undef LOCTEXT_NAMESPACE
	
IMPLEMENT_MODULE(FDebugTestPluginModule, DebugTestPlugin)