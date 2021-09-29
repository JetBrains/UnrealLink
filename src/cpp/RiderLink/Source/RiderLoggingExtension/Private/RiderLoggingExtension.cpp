#include "RiderLoggingExtension.hpp"

#include "BlueprintProvider.hpp"
#include "IRiderLink.hpp"
#include "Model/Library/UE4Library/LogMessageInfo.Generated.h"
#include "Model/Library/UE4Library/StringRange.Generated.h"
#include "Model/Library/UE4Library/UnrealLogEvent.Generated.h"

#include "Internationalization/Regex.h"
#include "Misc/DateTime.h"
#include "Modules/ModuleManager.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLoggingExtensionModule);

IMPLEMENT_MODULE(FRiderLoggingExtensionModule, RiderLoggingExtension);

namespace LoggingExtensionImpl
{
static TArray<rd::Wrapper<JetBrains::EditorPlugin::StringRange>> GetPathRanges(
	const FRegexPattern& Pattern,
	const FString& Str)
{
	using JetBrains::EditorPlugin::StringRange;
	FRegexMatcher Matcher(Pattern, Str);
	TArray<rd::Wrapper<StringRange>> Ranges;
	while (Matcher.FindNext())
	{
		const int Start = Matcher.GetMatchBeginning();
		const int End = Matcher.GetMatchEnding();
		FString PathName = Str.Mid(Start, End - Start - 1);
		if (BluePrintProvider::IsBlueprint(PathName))
			Ranges.Emplace(StringRange(Start, End));
	}
	return Ranges;
}

static TArray<rd::Wrapper<JetBrains::EditorPlugin::StringRange>> GetMethodRanges(
	const FRegexPattern& Pattern,
	const FString& Str)
{
	using JetBrains::EditorPlugin::StringRange;
	FRegexMatcher Matcher(Pattern, Str);
	TArray<rd::Wrapper<StringRange>> Ranges;
	while (Matcher.FindNext())
	{
		Ranges.Emplace(StringRange(Matcher.GetMatchBeginning(), Matcher.GetMatchEnding()));
	}
	return Ranges;
}

static const FRegexPattern PathPattern = FRegexPattern(TEXT("[^\\s]*/[^\\s]+"));
static const FRegexPattern MethodPattern = FRegexPattern(TEXT("[0-9a-z_A-Z]+::~?[0-9a-z_A-Z]+"));

static bool SendMessageToRider(const JetBrains::EditorPlugin::LogMessageInfo& MessageInfo, const FString& Message)
{
	return IRiderLinkModule::Get().FireAsyncAction(
	[&MessageInfo, &Message] (JetBrains::EditorPlugin::RdEditorModel const& RdEditorModel)
	{
		rd::ISignal<JetBrains::EditorPlugin::UnrealLogEvent> const& UnrealLog = RdEditorModel.get_unrealLog();
		UnrealLog.fire({
			MessageInfo,
			Message,
			GetPathRanges(PathPattern, Message),
			GetMethodRanges(MethodPattern, Message)
		});
	});
}

void SendMessageInChunks(FString* Msg, const JetBrains::EditorPlugin::LogMessageInfo& MessageInfo)
{
	static int NUMBER_OF_CHUNKS = 1024;
	while (!Msg->IsEmpty())
	{
		SendMessageToRider(MessageInfo, Msg->Left(NUMBER_OF_CHUNKS));
		Msg->RightChopInline(NUMBER_OF_CHUNKS);
	}
}

void ScheduledSendMessage(FString* Msg, const JetBrains::EditorPlugin::LogMessageInfo& MessageInfo)
{
	FString ToSend;
	while (Msg->Split("\n", &ToSend, Msg))
	{
		SendMessageInChunks(&ToSend, MessageInfo);
	}

	SendMessageInChunks(Msg, MessageInfo);
}
}


void FRiderLoggingExtensionModule::StartupModule()
{
	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("STARTUP START"));

	static const auto START_TIME = FDateTime::UtcNow().ToUnixTimestamp();
	static const auto GetTimeNow = [](double Time) -> rd::DateTime
	{
		return rd::DateTime(START_TIME + static_cast<int64>(Time));
	};

	ModuleLifetimeDef = IRiderLinkModule::Get().CreateNestedLifetimeDefinition();
	LoggingScheduler = MakeUnique<rd::SingleThreadScheduler>(ModuleLifetimeDef.lifetime, "LoggingScheduler");
	ModuleLifetimeDef.lifetime->bracket(
	[this]()
	{
		OutputDevice.onSerializeMessage.BindLambda(
		[this](const TCHAR* msg, ELogVerbosity::Type Type, const class FName& Name, TOptional<double> Time)
		{
			if (Type > ELogVerbosity::All) return;

			rd::optional<rd::DateTime> DateTime;
			if (Time)
			{
				DateTime = GetTimeNow(Time.GetValue());
			}
			const FString PlainName = Name.GetPlainNameString();
			const JetBrains::EditorPlugin::LogMessageInfo MessageInfo{Type, PlainName, DateTime};
			LoggingScheduler->queue([Msg = FString(msg), MessageInfo]() mutable
			{
				LoggingExtensionImpl::ScheduledSendMessage(&Msg, MessageInfo);
			});
		});
	},
	[this]()
	{
		if (OutputDevice.onSerializeMessage.IsBound())
			OutputDevice.onSerializeMessage.Unbind();
	});

	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("STARTUP FINISH"));
}

void FRiderLoggingExtensionModule::ShutdownModule()
{
	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("SHUTDOWN START"));
	ModuleLifetimeDef.terminate();
	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("SHUTDOWN FINISH"));
}
