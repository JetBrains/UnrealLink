#include "PythonExecutor.hpp"
#include "RiderAgentTools.hpp"
#include "IPythonScriptPlugin.h"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"
#include "Async/Async.h"
#include "UObject/GarbageCollection.h"
#include "Engine/World.h"
#include "Engine/Engine.h"

static const int32 MAX_OUTPUT_CHARS = 10000;

static FString JoinLogOutput(const TArray<FPythonLogOutputEntry>& Entries)
{
	TArray<FString> Lines;
	Lines.Reserve(Entries.Num());
	for (const FPythonLogOutputEntry& Entry : Entries)
	{
		Lines.Add(Entry.Output);
	}
	return FString::Join(Lines, TEXT("\n"));
}

static FString CapString(const FString& Str)
{
	if (Str.Len() <= MAX_OUTPUT_CHARS) return Str;
	const int32 Half = MAX_OUTPUT_CHARS / 2;
	return Str.Left(Half) + TEXT("\n[... output truncated ...]\n") + Str.Right(Half);
}

using FScriptCallback = TFunction<void(JetBrains::EditorPlugin::ScriptResult)>;

// UE 5.7 dropped FPythonCommandEx::CommandError; failure traces are written to CommandResult.
static void SplitResultOrError(const FPythonCommandEx& Cmd, bool bSuccess, FString& OutResult, FString& OutError)
{
	if (bSuccess)
	{
		OutResult = CapString(Cmd.CommandResult);
		OutError = FString();
	}
	else
	{
		OutResult = FString();
		OutError = CapString(Cmd.CommandResult);
	}
}

// Configure FPythonCommandEx for the requested mode.
// isolated=true keeps the historic "evaluate a single expression and return its value" semantic.
// isolated=false runs the script as a file (multi-statement supported, shared __main__ globals
// across calls), matching the AgentBridge reference and avoiding the
// "multiple statements found while compiling a single statement" failure that
// EPythonCommandExecutionMode::ExecuteStatement imposes.
static void ConfigureCommand(FPythonCommandEx& Cmd, const FString& Script, bool bIsolated)
{
	Cmd.Command = Script;
	if (bIsolated)
	{
		Cmd.ExecutionMode = EPythonCommandExecutionMode::EvaluateStatement;
	}
	else
	{
		Cmd.ExecutionMode = EPythonCommandExecutionMode::ExecuteFile;
		Cmd.FileExecutionScope = EPythonFileExecutionScope::Public;
	}
}

// Release Python's UObject refs, then ask the engine to reclaim them — but NEVER
// collect synchronously from here. This runs inside an AsyncTask(GameThread) callback,
// which is not a guaranteed GC-safe point. An inline full CollectGarbage() can purge
// objects that are still mid-fixup after a Live Coding re-instance (e.g. a re-instanced
// character's TArray<TSubclassOf<UGameplayAbility>> DefaultAbilities), freeing an
// allocation that is then read through a dangling pointer → 0xC0000005 access violation
// in the allocator destructor.
//
// GEngine->ForceGarbageCollection() only raises a flag; UEngine services it at the
// engine's normal GC-safe point on a later tick, AFTER re-instancing fixups complete.
// Deferring also avoids two prior hazards:
//   - re-entrant GC: calling CollectGarbage inside another GC pass spawns TaskGraph
//     work that doesn't inherit FAppTime's game-thread context, tripping
//     EnsureFailed(IsInGameThread()) in AppTime.cpp (guarded below by IsGarbageCollecting).
//   - PIE: a synchronous CollectGarbage → ForceDeleteObjects can fail to unload
//     PIE-referenced packages (e.g. BB_Bot Blackboard) and fire an ensure at
//     ObjectTools.cpp:3963.
static void PostExecGarbageCollect(IPythonScriptPlugin* PythonPlugin)
{
	if (PythonPlugin && PythonPlugin->IsPythonAvailable())
	{
		FPythonCommandEx GcCommand;
		GcCommand.Command = TEXT("import gc; gc.collect()");
		GcCommand.ExecutionMode = EPythonCommandExecutionMode::ExecuteStatement;
		PythonPlugin->ExecPythonCommandEx(GcCommand);
	}

	// Always defer to the engine's next safe collection point; never collect inline here.
	if (GEngine && !IsGarbageCollecting())
	{
		GEngine->ForceGarbageCollection(false);
	}
}

static void ExecuteOnGameThread(const FString& Script, bool bIsolated, FScriptCallback Callback)
{
	AsyncTask(ENamedThreads::GameThread, [Script, bIsolated, Callback = MoveTemp(Callback)]()
	{
		using namespace JetBrains::EditorPlugin;

		auto* PythonPlugin = IPythonScriptPlugin::Get();
		if (!PythonPlugin || !PythonPlugin->IsPythonAvailable())
		{
			Callback(ScriptResult(
				false,
				FString(TEXT("")),
				FString(TEXT("")),
				FString(TEXT("Python plugin not available"))
			));
			return;
		}

		FPythonCommandEx Cmd;
		ConfigureCommand(Cmd, Script, bIsolated);

		const bool bSuccess = PythonPlugin->ExecPythonCommandEx(Cmd);
		FString Output = Cmd.LogOutput.Num() > 0
			                 ? CapString(JoinLogOutput(Cmd.LogOutput))
			                 : FString(TEXT(""));
		FString Result;
		FString Error;
		SplitResultOrError(Cmd, bSuccess, Result, Error);

		PostExecGarbageCollect(PythonPlugin);

		Callback(ScriptResult(bSuccess, MoveTemp(Output), MoveTemp(Result), MoveTemp(Error)));
	});
}

void PythonExecutor::BindTo(rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model)
{
	using namespace JetBrains::EditorPlugin;

	Model.get_executeScript().set(
		[](rd::Lifetime, ScriptRequest const& Request) -> rd::RdTask<ScriptResult>
		{
			rd::RdTask<ScriptResult> Task;
			ExecuteOnGameThread(
				Request.get_script(),
				Request.get_isolated(),
				[Task](ScriptResult Result) mutable { Task.set(MoveTemp(Result)); }
			);
			return Task;
		});

	Model.get_executeBatchScripts().set(
		[](rd::Lifetime, BatchScriptRequest const& Request) -> rd::RdTask<BatchScriptResult>
		{
			rd::RdTask<BatchScriptResult> Task;
			const TArray<FString> Scripts = Request.get_scripts();
			const int32 StartFrom = Request.get_startFrom();

			if (Scripts.Num() == 0 || StartFrom >= Scripts.Num())
			{
				Task.set(BatchScriptResult(TArray<rd::Wrapper<ScriptResult>>(), StartFrom - 1));
				return Task;
			}

			AsyncTask(ENamedThreads::GameThread,
			          [Scripts, StartFrom, Task]() mutable
			          {
				          auto* PythonPlugin = IPythonScriptPlugin::Get();
				          TArray<rd::Wrapper<ScriptResult>> Results;
				          Results.Reserve(Scripts.Num() - StartFrom);
				          int32 LastSuccessful = StartFrom - 1;

				          for (int32 i = StartFrom; i < Scripts.Num(); ++i)
				          {
					          FPythonCommandEx Cmd;
					          // Batch scripts run as files (multi-statement, shared globals across steps).
					          ConfigureCommand(Cmd, Scripts[i], /*bIsolated=*/false);

					          const bool bSuccess = PythonPlugin && PythonPlugin->IsPythonAvailable()
						          && PythonPlugin->ExecPythonCommandEx(Cmd);

					          FString Output = Cmd.LogOutput.Num() > 0
						                           ? CapString(JoinLogOutput(Cmd.LogOutput))
						                           : FString(TEXT(""));
					          FString Result;
					          FString Error;
					          SplitResultOrError(Cmd, bSuccess, Result, Error);

					          PostExecGarbageCollect(PythonPlugin);

					          Results.Emplace(ScriptResult(bSuccess, MoveTemp(Output), MoveTemp(Result),
					                                       MoveTemp(Error)));

					          if (!bSuccess)
					          {
						          Task.set(BatchScriptResult(MoveTemp(Results), LastSuccessful));
						          return;
					          }
					          LastSuccessful = i;
				          }

				          Task.set(BatchScriptResult(MoveTemp(Results), LastSuccessful));
			          });

			return Task;
		});
}
