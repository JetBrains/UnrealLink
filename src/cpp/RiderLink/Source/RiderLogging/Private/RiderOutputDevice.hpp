#pragma once

#include "Misc/OutputDevice.h"
#include "Delegates/Delegate.h"
#include "Logging/LogVerbosity.h"

using FOnSerializeMessage = TDelegate<void(const TCHAR*, ELogVerbosity::Type, const FName&, TOptional<double>), FDefaultTSDelegateUserPolicy>;

class FRiderOutputDevice : public FOutputDevice {
public:
	void Setup(TFunction<FOnSerializeMessage::TFuncType>);
	virtual void TearDown() override;

protected:
	virtual void Serialize(const TCHAR* V, ELogVerbosity::Type Verbosity, const FName& Category) override;
	virtual void Serialize(const TCHAR* V, ELogVerbosity::Type Verbosity, const FName& Category, double Time) override;
	
private:
	FOnSerializeMessage onSerializeMessage;
	FCriticalSection CriticalSection;
};
