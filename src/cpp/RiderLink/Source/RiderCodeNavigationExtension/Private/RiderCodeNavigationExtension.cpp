#include "RiderCodeNavigationExtension.hpp"

#include "Modules/ModuleManager.h"

#define LOCTEXT_NAMESPACE "RiderLink"

DEFINE_LOG_CATEGORY(FLogRiderCodeNavigationExtensionModule);

IMPLEMENT_MODULE(FRiderCodeNavigationExtensionModule, RiderCodeNavigationExtension);

void FRiderCodeNavigationExtensionModule::StartupModule()
{
	UE_LOG(FLogRiderCodeNavigationExtensionModule, Verbose, TEXT("STARTUP START"));
	FSourceCodeNavigation::AddNavigationHandler(&RiderSourceCodeNavigationHandler);
	UE_LOG(FLogRiderCodeNavigationExtensionModule, Verbose, TEXT("STARTUP FINISH"));
}

void FRiderCodeNavigationExtensionModule::ShutdownModule()
{
	UE_LOG(FLogRiderCodeNavigationExtensionModule, Verbose, TEXT("SHUTDOWN START"));
	FSourceCodeNavigation::RemoveNavigationHandler(&RiderSourceCodeNavigationHandler);
	UE_LOG(FLogRiderCodeNavigationExtensionModule, Verbose, TEXT("SHUTDOWN FINISH"));
}
