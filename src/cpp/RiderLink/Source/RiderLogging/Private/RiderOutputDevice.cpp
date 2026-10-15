#include "RiderOutputDevice.hpp"

#include "CoreGlobals.h"
#include "HAL/PlatformTime.h"
#include "Misc/ScopeLock.h"
#include "Misc/OutputDeviceRedirector.h"
#if RIDER_HAS_SERIALIZE_RECORD
#include "Logging/StructuredLog.h"
#include "Misc/StringBuilder.h"
#endif

namespace
{
// Time is seconds since GStartTime; convert it to UTC relative to the current moment
FDateTime LogTimeToUtc(double Time)
{
	const double SecondsAgo = FPlatformTime::Seconds() - GStartTime - Time;
	return FDateTime::UtcNow() - FTimespan::FromSeconds(SecondsAgo);
}
}

void FRiderOutputDevice::Serialize(const TCHAR* V, ELogVerbosity::Type Verbosity, const FName& Category)
{
	Broadcast(V, Verbosity, Category, FDateTime::UtcNow());
}

void FRiderOutputDevice::Serialize(const TCHAR* V, ELogVerbosity::Type Verbosity, const FName& Category, double Time)
{
	// Negative time means it wasn't provided
	const FDateTime UtcTime = Time >= 0.0 ? LogTimeToUtc(Time) : FDateTime::UtcNow();
	Broadcast(V, Verbosity, Category, UtcTime);
}

#if RIDER_HAS_SERIALIZE_RECORD
void FRiderOutputDevice::SerializeRecord(const UE::FLogRecord& Record)
{
	TStringBuilder<512> Text;
	Record.FormatMessageTo(Text);
	const FDateTime RecordTime = Record.GetTime().GetUtcTime();
	FDateTime RealTime = RecordTime.GetTicks() != 0 ? RecordTime : FDateTime::UtcNow();
#if ENGINE_MAJOR_VERSION < 6
	FName CategoryName = Record.GetCategory();
#else
	FName CategoryName = Record.GetCategoryName();
#endif
	Broadcast(*Text, Record.GetVerbosity(), CategoryName, RealTime);
}
#endif

void FRiderOutputDevice::Broadcast(const TCHAR* V, ELogVerbosity::Type Verbosity, const FName& Category, const FDateTime& Time)
{
	FScopeLock Lock{&CriticalSection};
	
	onSerializeMessage.ExecuteIfBound(V, Verbosity, Category, Time);
}

FRiderOutputDevice::~FRiderOutputDevice()
{
	// At shutdown, GLog may already be null
	// This should be out of scoped lock, because it'll implicitly try take a lock in OutputDeviceRedirector
	// And Serialize is already holding that lock, so we'll have a deadlock here
	if (GLog != nullptr)
	{
		GLog->RemoveOutputDevice(this);
	}
}

void FRiderOutputDevice::Setup(TFunction<FOnSerializeMessage::TFuncType> Callback)
{
	FScopeLock Lock{&CriticalSection};

	if(onSerializeMessage.IsBound()) return;
	
	onSerializeMessage.BindLambda(Callback);
	GLog->AddOutputDevice(this);
#if ENGINE_MAJOR_VERSION < 5 || (ENGINE_MAJOR_VERSION == 5 && ENGINE_MINOR_VERSION < 7)
	GLog->SerializeBacklog(this);
#endif
}

void FRiderOutputDevice::TearDown()
{
	FScopeLock Lock{&CriticalSection};

	if(onSerializeMessage.IsBound() == false) return;

	onSerializeMessage.Unbind();
}
