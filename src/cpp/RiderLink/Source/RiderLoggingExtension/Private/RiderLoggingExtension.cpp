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

static TArray<rd::Wrapper<JetBrains::EditorPlugin::StringRange>> GetPathRanges(
	const FRegexPattern& Pattern, const FString& Str)
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
	const FRegexPattern& Pattern, const FString& Str)
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

void FRiderLoggingExtensionModule::StartupModule()
{
	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("STARTUP START"));

	static const auto START_TIME = FDateTime::UtcNow();
	static const auto GetTimeNow = [](double Time) -> rd::DateTime
	{
		return rd::DateTime(static_cast<std::time_t>(START_TIME.ToUnixTimestamp() +
			static_cast<int64>(Time)));
	};

	IRiderLinkModule& RiderLinkModule = IRiderLinkModule::Get();
	ModuleLifetimeDef = RiderLinkModule.CreateNestedLifetimeDefinition();
	RiderLinkModule.ViewModel(ModuleLifetimeDef.lifetime,
	[this](rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& RdEditorModel)
    {
        ModelLifetime->bracket(
        [this, &RdEditorModel]()
       {
           outputDevice.onSerializeMessage.BindLambda(
               [this, &RdEditorModel](
               const TCHAR* msg, ELogVerbosity::Type Type,
               const class FName& Name, TOptional<double> Time)
               {
                   if (Type > ELogVerbosity::All) return;

                   rd::ISignal<
                           JetBrains::EditorPlugin::UnrealLogEvent>
                       const& UnrealLog = RdEditorModel.
                           get_unrealLog();
                   rd::optional<rd::DateTime> DateTime;
                   if (Time)
                   {
                       DateTime = GetTimeNow(Time.GetValue());
                   }
                   const FString Msg = FString(msg);
                   const FString PlainName = Name.
                       GetPlainNameString();

                   JetBrains::EditorPlugin::LogMessageInfo
                       MessageInfo{Type, PlainName, DateTime};

                   // [HACK]: fix https://github.com/JetBrains/UnrealLink/issues/17
                   // while we won't change BP hyperlink parsing
                   FString Tail = Msg.Left(4096);

                   const FRegexPattern PathPattern = FRegexPattern(
                       TEXT("[^\\s]*/[^\\s]+"));
                   const FRegexPattern MethodPattern =
                       FRegexPattern(
                           TEXT("[0-9a-z_A-Z]+::~?[0-9a-z_A-Z]+"));
                   FString ToSend;
                   while (Tail.Split("\n", &ToSend, &Tail))
                   {
                       ToSend.TrimEndInline();
                       UnrealLog.fire(
                           JetBrains::EditorPlugin::UnrealLogEvent{
                               MessageInfo, ToSend,
                               GetPathRanges(PathPattern, ToSend),
                               GetMethodRanges(
                                   MethodPattern, ToSend)
                           });
                   }
                   Tail.TrimEndInline();
                   UnrealLog.fire(
                       JetBrains::EditorPlugin::UnrealLogEvent{
                           MessageInfo, Tail,
                           GetPathRanges(PathPattern, Tail),
                           GetMethodRanges(MethodPattern, Tail)
                       });
               });
       },
       [this]()
       {
			if (outputDevice.onSerializeMessage.IsBound())
			    outputDevice.onSerializeMessage.Unbind();
       });
    });

	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("STARTUP FINISH"));
}

void FRiderLoggingExtensionModule::ShutdownModule()
{
	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("SHUTDOWN START"));
	ModuleLifetimeDef.terminate();
	UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("SHUTDOWN FINISH"));
}
