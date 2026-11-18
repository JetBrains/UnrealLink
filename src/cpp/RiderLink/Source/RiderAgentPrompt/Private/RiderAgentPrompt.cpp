#include "RiderAgentPrompt.h"

#include "PromptGestureProcessor.hpp"

#define LOCTEXT_NAMESPACE "FRiderAgentPromptModule"
DEFINE_LOG_CATEGORY(FLogRiderAgentPromptModule);
IMPLEMENT_MODULE(FRiderAgentPromptModule, RiderAgentPrompt);

void FRiderAgentPromptModule::StartupModule()
{
	PromptGestureProcessor = FPromptGestureProcessor::Register();
}

void FRiderAgentPromptModule::ShutdownModule()
{
    FPromptGestureProcessor::Unregister(PromptGestureProcessor);
}

#undef LOCTEXT_NAMESPACE
