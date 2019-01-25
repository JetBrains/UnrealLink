#include "Misc/OutputDevice.h"

DECLARE_DELEGATE_OneParam(FOnSerializeMessage, const TCHAR*);

class FRiderOutputDevice : public FOutputDevice
{
public:

	FRiderOutputDevice()
	{
		GLog->AddOutputDevice(this);
		GLog->SerializeBacklog(this);
	}

	~FRiderOutputDevice()
	{
		if (onSerializeMessage.IsBound())
			onSerializeMessage.Unbind();
		// At shutdown, GLog may already be null
		if( GLog != NULL )
		{
			GLog->RemoveOutputDevice(this);
		}
	}

    FOnSerializeMessage onSerializeMessage;

protected:

	virtual void Serialize( const TCHAR* V, ELogVerbosity::Type Verbosity, const class FName& Category ) override
	{
        onSerializeMessage.ExecuteIfBound(V);
	}

private:
};