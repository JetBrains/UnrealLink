#include "RiderLoggingExtension.hpp"

#include "RiderLink.hpp"
#include "BlueprintProvider.hpp"
#include "RdEditorProtocol/UE4Library/LogMessageInfo.h"
#include "RdEditorProtocol/UE4Library/StringRange.h"
#include "RdEditorProtocol/UE4Library/UnrealLogEvent.h"

#include "Internationalization/Regex.h"
#include "Misc/DateTime.h"
#include "Modules/ModuleManager.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLoggingExtensionModule);

IMPLEMENT_MODULE(FRiderLoggingExtensionModule, RiderLoggingExtension);

static TArray<rd::Wrapper<Jetbrains::EditorPlugin::StringRange>> GetPathRanges(const FRegexPattern &Pattern, const FString &Str)
{
    using Jetbrains::EditorPlugin::StringRange;
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

static TArray<rd::Wrapper<Jetbrains::EditorPlugin::StringRange>> GetMethodRanges(const FRegexPattern &Pattern, const FString &Str)
{
    using Jetbrains::EditorPlugin::StringRange;
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

    FRiderLinkModule& RiderLinkModule = FRiderLinkModule::Get();

    RdConnection& RdConnection = RiderLinkModule.RdConnection;

    outputDevice.onSerializeMessage.BindLambda(
        [this, &RdConnection](const TCHAR* msg, ELogVerbosity::Type Type,
               const class FName& Name, TOptional<double> Time)
        {
            if (Type > ELogVerbosity::All) return;

            rd::ISignal<Jetbrains::EditorPlugin::UnrealLogEvent> const & UnrealLog = RdConnection.UnrealToBackendModel.get_unrealLog();
            RdConnection.Scheduler.queue([this, &UnrealLog, tail = FString(msg), Type,
                    Name = Name.GetPlainNameString(),
                    Time]() mutable
                {
                    rd::optional<rd::DateTime> DateTime;
                    if (Time)
                    {
                        DateTime = GetTimeNow(Time.GetValue());
                    }
                    Jetbrains::EditorPlugin::LogMessageInfo MessageInfo =
                        Jetbrains::EditorPlugin::LogMessageInfo(Type, Name, DateTime);

                    // [HACK]: fix https://github.com/JetBrains/UnrealLink/issues/17
                    // while we won't change BP hyperlink parsing
                    tail = tail.Left(4096);

                    const FRegexPattern PathPattern = FRegexPattern(TEXT("[^\\s]*/[^\\s]+"));
                    const FRegexPattern MethodPattern = FRegexPattern(TEXT("[0-9a-z_A-Z]+::[0-9a-z_A-Z]+"));
                    FString toSend;
                    while (tail.Split("\n", &toSend, &tail))
                    {
                        toSend.TrimEndInline();
                        UnrealLog.fire(
                                Jetbrains::EditorPlugin::UnrealLogEvent(
                                    MessageInfo, toSend, GetPathRanges(PathPattern, toSend), GetMethodRanges(MethodPattern, toSend)
                                ));
                    }
                    tail.TrimEndInline();
                    UnrealLog.fire(
                        Jetbrains::EditorPlugin::UnrealLogEvent{
                            MessageInfo, tail, GetPathRanges(PathPattern, tail), GetMethodRanges(MethodPattern, tail)
                        });
                });
        });

    UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("STARTUP FINISH"));
}

void FRiderLoggingExtensionModule::ShutdownModule()
{
    UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("SHUTDOWN START"));
    outputDevice.onSerializeMessage.Unbind();
    UE_LOG(FLogRiderLoggingExtensionModule, Verbose, TEXT("SHUTDOWN FINISH"));
}
