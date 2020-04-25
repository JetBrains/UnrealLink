#include "RiderLoggingExtension.hpp"

#include "RiderLink.hpp"
#include "RdEditorProtocol/UE4Library/UnrealLogEvent.h"

#include "Misc/DateTime.h"
#include "Modules/ModuleManager.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderLoggingExtensionModule);

IMPLEMENT_MODULE(FRiderLoggingExtensionModule, RiderLoggingExtension);

void FRiderLoggingExtensionModule::StartupModule()
{
    UE_LOG(FLogRiderLoggingExtensionModule, Log, TEXT("STARTUP START"));

    static const auto START_TIME = FDateTime::Now();
    static const auto GetTimeNow = [](double Time) -> rd::DateTime
    {
        return rd::DateTime(static_cast<std::time_t>(START_TIME.ToUnixTimestamp() +
            static_cast<int64>(Time)));
    };
    outputDevice.onSerializeMessage.BindLambda(
        [this](const TCHAR* Message, ELogVerbosity::Type Type,
               const class FName& Name, TOptional<double> Time)
        {
            if (Type > ELogVerbosity::All) return;

            FRiderLinkModule& RiderLinkModule = FModuleManager::GetModuleChecked<FRiderLinkModule>(
                FRiderLinkModule::GetModuleName());
            RiderLinkModule.rdConnection.Scheduler.queue(
                [this, message = FString(Message), Type,
                    Name = Name.GetPlainNameString(),
                    Time]() mutable
                {
                    rd::optional<rd::DateTime> DateTime;
                    if (Time)
                    {
                        DateTime = GetTimeNow(Time.GetValue());
                    }
                    auto MessageInfo = Jetbrains::EditorPlugin::LogMessageInfo(
                        Type, Name, DateTime);
                    FRiderLinkModule& RiderLinkModule = FModuleManager::GetModuleChecked<
                        FRiderLinkModule>(
                        FRiderLinkModule::GetModuleName());
                    RiderLinkModule.rdConnection.UnrealToBackendModel.get_unrealLog().fire(
                        Jetbrains::EditorPlugin::UnrealLogEvent{
                            std::move(MessageInfo), std::move(message)
                        });
                });
        });

    UE_LOG(FLogRiderLoggingExtensionModule, Log, TEXT("STARTUP FINISH"));
}

void FRiderLoggingExtensionModule::ShutdownModule()
{
    UE_LOG(FLogRiderLoggingExtensionModule, Log, TEXT("SHUTDOWN START"));
    outputDevice.onSerializeMessage.Unbind();
    UE_LOG(FLogRiderLoggingExtensionModule, Log, TEXT("SHUTDOWN FINISH"));
}
