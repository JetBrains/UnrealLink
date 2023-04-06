#include "RiderLC.hpp"

#include "IRiderLink.hpp"
#include "RdEditorModel/RdEditorModel.Generated.h"
#include "task/RdEndpoint.h"

#include "Async/Async.h"
#include "ILiveCodingModule.h"

#define LOCTEXT_NAMESPACE "FRiderLCModule"

DEFINE_LOG_CATEGORY(FLogRiderLCModule);


void WrapRDCall(rd::RdEndpoint<rd::Void, bool, rd::Polymorphic<rd::Void>, rd::Polymorphic<bool>> const & Call, TFunction<bool(const ILiveCodingModule&)> LocalCall)
{
	Call.set([LocalCall](rd::Void const&)-> bool
	{
		const ILiveCodingModule* LiveCodingModule = FModuleManager::GetModulePtr<ILiveCodingModule>(LIVE_CODING_MODULE_NAME);
		if(LiveCodingModule != nullptr)
		{
			return LocalCall(*LiveCodingModule);
		}
		return false;
	});
}

void FRiderLCModule::SetupLiveCodingBinds()
{	
	ILiveCodingModule& LiveCoding = FModuleManager::LoadModuleChecked<ILiveCodingModule>(LIVE_CODING_MODULE_NAME);
	IRiderLinkModule& RiderLinkModule = IRiderLinkModule::Get();
	PatchCompleteHandle = LiveCoding.GetOnPatchCompleteDelegate().AddLambda([this, &RiderLinkModule]
	{
		RiderLinkModule.QueueModelAction([](JetBrains::EditorPlugin::RdEditorModel const& RdEditorModel)
		{
			RdEditorModel.get_lC_OnPatchComplete().fire();
		});
	});

	RiderLinkModule.ViewModel(ModuleLifetimeDef.lifetime, [](rd::Lifetime Lifetime, JetBrains::EditorPlugin::RdEditorModel const& RdEditorModel)
	{
		RdEditorModel.get_lC_Compile().advise(Lifetime, []
		{
			AsyncTask(ENamedThreads::GameThread, []
			{
				ILiveCodingModule* LiveCodingModule = FModuleManager::GetModulePtr<ILiveCodingModule>(LIVE_CODING_MODULE_NAME);
				if(LiveCodingModule != nullptr)
				{
					LiveCodingModule->Compile();
				}				
			});
		});

		RdEditorModel.get_lC_EnableByDefault().advise(Lifetime, [](bool Enable)
		{
			ILiveCodingModule* LiveCodingModule = FModuleManager::GetModulePtr<ILiveCodingModule>(LIVE_CODING_MODULE_NAME);
			if(LiveCodingModule != nullptr)
			{
				LiveCodingModule->EnableByDefault(Enable);
			}			
		});

		RdEditorModel.get_lC_EnableForSession().advise(Lifetime, [](bool Enable)
		{
			ILiveCodingModule* LiveCodingModule = FModuleManager::GetModulePtr<ILiveCodingModule>(LIVE_CODING_MODULE_NAME);
			if(LiveCodingModule != nullptr)
			{
				LiveCodingModule->EnableForSession(Enable);
			}			
		});
		
		WrapRDCall(RdEditorModel.get_lC_HasStarted(), [](const ILiveCodingModule& LiveCodingModule)
		{
			return LiveCodingModule.HasStarted();
		});
		
		WrapRDCall(RdEditorModel.get_lC_IsCompiling(), [](const ILiveCodingModule& LiveCodingModule)
		{
			return LiveCodingModule.IsCompiling();
		});
		
		WrapRDCall(RdEditorModel.get_lC_IsEnabledByDefault(), [](const ILiveCodingModule& LiveCodingModule)
		{
			return LiveCodingModule.IsEnabledByDefault();
		});
		
		WrapRDCall(RdEditorModel.get_lC_IsEnabledForSession(), [](const ILiveCodingModule& LiveCodingModule)
		{
			return LiveCodingModule.IsEnabledForSession();
		});
		
		WrapRDCall(RdEditorModel.get_lC_CanEnableForSession(), [](const ILiveCodingModule& LiveCodingModule)
		{
			return LiveCodingModule.CanEnableForSession();
		});
	});
}

void FRiderLCModule::StartupModule()
{
	UE_LOG(FLogRiderLCModule, Verbose, TEXT("RiderLC STARTUP START"));
	
	const IRiderLinkModule& RiderLinkModule = IRiderLinkModule::Get();
	ModuleLifetimeDef = RiderLinkModule.CreateNestedLifetimeDefinition();
	SetupLiveCodingBinds();
	
	UE_LOG(FLogRiderLCModule, Verbose, TEXT("RiderLC STARTUP FINISH"));
}

void FRiderLCModule::ShutdownModule()
{
	UE_LOG(FLogRiderLCModule, Verbose, TEXT("RiderLC SHUTDOWN START"));
	
	ILiveCodingModule& LiveCoding = FModuleManager::LoadModuleChecked<ILiveCodingModule>(LIVE_CODING_MODULE_NAME);
	LiveCoding.GetOnPatchCompleteDelegate().Remove(PatchCompleteHandle);
	ModuleLifetimeDef.terminate();
	
	UE_LOG(FLogRiderLCModule, Verbose, TEXT("RiderLC SHUTDOWN FINISH"));
    
}

#undef LOCTEXT_NAMESPACE
    
IMPLEMENT_MODULE(FRiderLCModule, RiderLC)