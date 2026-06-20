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
//
// isolated=true → ExecuteFile + Private scope: a persistent dict separate from __main__.
// After execution ClearPrivateScope() wipes all user-set vars, releasing UObject refs so
// FPyReferenceCollector drops them and UE GC can reclaim the objects. Use for any script
// that creates, loads, or compiles Blueprint assets — without the explicit clear, the
// Blueprint stays pinned across calls, blocks deletion/recompile, and can crash the editor.
//
// isolated=false → ExecuteFile + Public scope: shared __main__ globals persist across
// calls (cross-call variable sharing). Appropriate for batch steps that explicitly share
// state; do NOT use for independent single tool calls.
static void ConfigureCommand(FPythonCommandEx& Cmd, const FString& Script, bool bIsolated)
{
	Cmd.Command = Script;
	Cmd.ExecutionMode = EPythonCommandExecutionMode::ExecuteFile;
	Cmd.FileExecutionScope = bIsolated
		? EPythonFileExecutionScope::Private
		: EPythonFileExecutionScope::Public;
}

// Release Python's UObject refs and schedule engine GC — but NEVER collect synchronously.
//
// This runs inside an AsyncTask(GameThread) callback, which is not a guaranteed GC-safe
// point. An inline CollectGarbage() can purge objects still mid-fixup after a Live Coding
// re-instance (e.g. a re-instanced character's TArray<TSubclassOf<UGameplayAbility>>),
// freeing memory that is then read through a dangling pointer → 0xC0000005 access violation.
//
// GEngine->ForceGarbageCollection() only raises a flag; UEngine services it at the next
// GC-safe tick, after re-instancing fixups complete. Deferring also avoids:
//   - re-entrant GC: CollectGarbage inside another GC pass spawns TaskGraph work that
//     doesn't inherit FAppTime's game-thread context, tripping EnsureFailed(IsInGameThread).
//   - PIE: synchronous ForceDeleteObjects can fail to unload PIE-referenced packages.
static void PostExecGarbageCollect(IPythonScriptPlugin* PythonPlugin)
{
	if (PythonPlugin && PythonPlugin->IsPythonAvailable())
	{
		FPythonCommandEx GcCommand;
		GcCommand.Command = TEXT("import gc; gc.collect()");
		GcCommand.ExecutionMode = EPythonCommandExecutionMode::ExecuteStatement;
		PythonPlugin->ExecPythonCommandEx(GcCommand);
	}

	if (GEngine && !IsGarbageCollecting())
	{
		GEngine->ForceGarbageCollection(false);
	}
}

// EPythonFileExecutionScope::Private is a single persistent dict shared across ALL
// Private-scoped calls — it is NOT a fresh namespace per call. Variables set in one
// call accumulate there and keep UObject ref counts alive, blocking GC.
// After every isolated execution we explicitly clear that dict so Python releases
// all refs and FPyReferenceCollector can drop the objects before the next GC tick.
static void ClearPrivateScope(IPythonScriptPlugin* PythonPlugin)
{
	FPythonCommandEx ClearCmd;
	// list(globals()) snapshots the keys before we start deleting.
	// Skip dunder names (__builtins__, __doc__, etc.) which Python needs internally.
	ClearCmd.Command = TEXT("[globals().pop(k) for k in list(globals()) if not k.startswith('__')]");
	ClearCmd.ExecutionMode = EPythonCommandExecutionMode::ExecuteFile;
	ClearCmd.FileExecutionScope = EPythonFileExecutionScope::Private; // same dict as user script
	PythonPlugin->ExecPythonCommandEx(ClearCmd);
}

static JetBrains::EditorPlugin::ScriptResult RunScript(
	IPythonScriptPlugin* PythonPlugin, const FString& Script, bool bIsolated)
{
	using namespace JetBrains::EditorPlugin;

	if (!PythonPlugin || !PythonPlugin->IsPythonAvailable())
	{
		return ScriptResult(false, FString(), FString(), TEXT("Python plugin not available"));
	}

	FPythonCommandEx Cmd;
	ConfigureCommand(Cmd, Script, bIsolated);

	const bool bSuccess = PythonPlugin->ExecPythonCommandEx(Cmd);

	FString Output = Cmd.LogOutput.Num() > 0
		                 ? CapString(JoinLogOutput(Cmd.LogOutput))
		                 : FString();
	FString Result;
	FString Error;
	SplitResultOrError(Cmd, bSuccess, Result, Error);

	if (bIsolated)
	{
		ClearPrivateScope(PythonPlugin);
	}
	PostExecGarbageCollect(PythonPlugin);

	return ScriptResult(bSuccess, MoveTemp(Output), MoveTemp(Result), MoveTemp(Error));
}

// RequestLifetime is the per-call lifetime that RD terminates when the client
// cancels or times out. We check it at two points:
//   1. Before executing Python — skip the work entirely if already cancelled.
//   2. Before Task.set() — calling set() on a cancelled RdTask corrupts the
//      model connection and makes all subsequent calls return "Cancelled" forever.
static void ExecuteOnGameThread(const FString& Script, rd::Lifetime RequestLifetime, FScriptCallback Callback)
{
	AsyncTask(ENamedThreads::GameThread, [Script, RequestLifetime, Callback = MoveTemp(Callback)]()
	{
		if (RequestLifetime->is_terminated())
			return;
		auto* PythonPlugin = IPythonScriptPlugin::Get();
		// Single tool calls always use Private scope: each call gets a fresh __main__ dict,
		// so UObject refs (Blueprints, assets) are released as soon as the script returns.
		// Public scope is only appropriate for batch steps that intentionally share state.
		Callback(RunScript(PythonPlugin, Script, /*bIsolated=*/true));
	});
}

void PythonExecutor::BindTo(rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model)
{
	using namespace JetBrains::EditorPlugin;

	Model.get_executeScript().set(
		[](rd::Lifetime RequestLifetime, ScriptRequest const& Request) -> rd::RdTask<ScriptResult>
		{
			rd::RdTask<ScriptResult> Task;
			ExecuteOnGameThread(
				Request.get_script(),
				RequestLifetime,
				[Task, RequestLifetime](ScriptResult Result) mutable
				{
					if (!RequestLifetime->is_terminated())
						Task.set(MoveTemp(Result));
				}
			);
			return Task;
		});

	Model.get_executeBatchScripts().set(
		[](rd::Lifetime RequestLifetime, BatchScriptRequest const& Request) -> rd::RdTask<BatchScriptResult>
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
			          [Scripts, StartFrom, Task, RequestLifetime]() mutable
			          {
				          if (RequestLifetime->is_terminated())
					          return;
				          auto* PythonPlugin = IPythonScriptPlugin::Get();
				          TArray<rd::Wrapper<ScriptResult>> Results;
				          Results.Reserve(Scripts.Num() - StartFrom);
				          int32 LastSuccessful = StartFrom - 1;

				          for (int32 i = StartFrom; i < Scripts.Num(); ++i)
				          {
					          if (RequestLifetime->is_terminated())
						          return;
					          // Batch scripts always run as files (multi-statement, shared globals across steps).
					          ScriptResult StepResult = RunScript(PythonPlugin, Scripts[i], /*bIsolated=*/false);
					          const bool bSuccess = StepResult.get_success();

					          Results.Emplace(MoveTemp(StepResult));

					          if (!bSuccess)
					          {
						          if (!RequestLifetime->is_terminated())
							          Task.set(BatchScriptResult(MoveTemp(Results), LastSuccessful));
						          return;
					          }
					          LastSuccessful = i;
				          }

				          if (!RequestLifetime->is_terminated())
					          Task.set(BatchScriptResult(MoveTemp(Results), LastSuccessful));
			          });

			return Task;
		});
}
