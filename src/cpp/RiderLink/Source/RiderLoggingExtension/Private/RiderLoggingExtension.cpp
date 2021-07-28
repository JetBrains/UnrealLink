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

/**
 * @brief 
 All data required for sending log message lives on main [game] thread.
 FRiderLoggingExtensionModule depends on FRiderLinkModule, so we're safe to get handler here.
 The only point of sync is RdEditorModel, so we wrap it in IRiderLinkModule::FireAsyncAction.
 */
static bool SendMessageToRider(const JetBrains::EditorPlugin::LogMessageInfo& MessageInfo, const FString& Message)
{
	return IRiderLinkModule::Get().FireAsyncAction(
[&MessageInfo, &Message]
		(JetBrains::EditorPlugin::RdEditorModel const& RdEditorModel)
		{
			rd::ISignal<JetBrains::EditorPlugin::UnrealLogEvent> const& UnrealLog = RdEditorModel.get_unrealLog();
			UnrealLog.fire({
				MessageInfo,
				Message,
				GetPathRanges(PathPattern, Message),
				GetMethodRanges(MethodPattern, Message)
			});
		}
	);
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

	// Even though we don't use LifetimeDefinition here, calling to IRiderLinkModule::Get() ensures that RiderLinkModule
	// will be initialized before FRiderLoggingExtensionModule, so we'll be safe to call it in output device handler.
	ModuleLifetimeDef = IRiderLinkModule::Get().CreateNestedLifetimeDefinition();
	
	// Subscribe on main [game] thread
	outputDevice.onSerializeMessage.BindLambda(
		// Log is called on main [game] thread
		[] ( const TCHAR* msg, ELogVerbosity::Type Type, const class FName& Name, TOptional<double> Time)
		{
			if (Type > ELogVerbosity::All) return;

			rd::optional<rd::DateTime> DateTime;
			if (Time)
			{
				DateTime = GetTimeNow(Time.GetValue());
			}
			const FString Msg = FString(msg);
			const FString PlainName = Name.GetPlainNameString();

			const JetBrains::EditorPlugin::LogMessageInfo MessageInfo{Type, PlainName, DateTime};

			// [HACK]: fix https://github.com/JetBrains/UnrealLink/issues/17
			// while we won't change BP hyperlink parsing
			FString Tail = Msg.Left(4096);

			FString ToSend;
			while (Tail.Split("\n", &ToSend, &Tail))
			{
				ToSend.TrimEndInline();
				LoggingExtensionImpl::SendMessageToRider(MessageInfo, ToSend);
			}
			
			Tail.TrimEndInline();            
			LoggingExtensionImpl::SendMessageToRider(MessageInfo, Tail);
        }
    );

	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("STARTUP FINISH"));
}

void FRiderLoggingExtensionModule::ShutdownModule()
{
	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("SHUTDOWN START"));
	// FRiderLoggingExtensionModule::ShutdownModule() before FRiderLinkModule::Shutdown()
	// RiderLinkModule will be still valid in output device handler
	if (outputDevice.onSerializeMessage.IsBound())
		outputDevice.onSerializeMessage.Unbind();
	ModuleLifetimeDef.terminate();
	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("SHUTDOWN FINISH"));
}
