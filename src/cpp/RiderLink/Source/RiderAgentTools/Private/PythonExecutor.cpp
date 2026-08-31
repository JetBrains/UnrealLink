#include "PythonExecutor.hpp"
#include "PythonRunContext.hpp"
#include "RiderAgentTools.hpp"
#include "RiderLogMacros.h"
#include "IPythonScriptPlugin.h"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"
#include "Async/Async.h"
#include "HAL/PlatformAtomics.h"
#include "Misc/ScopedSlowTask.h"
#include "UObject/GarbageCollection.h"
#include "Engine/World.h"
#include "Engine/Engine.h"

#include <atomic>

static const int32 MAX_OUTPUT_CHARS = 10000;

// How long a script must run before a progress dialog is worth putting on screen. Short
// scripts - the overwhelming majority - never see one.
static const double PROGRESS_DIALOG_DELAY_SECONDS = 1.5;

// Floor on how often a script can make us pump Slate, however often it calls tick().
static const double PROGRESS_PUMP_INTERVAL_SECONDS = 0.1;

namespace
{
	// Set from the RD thread when the client cancels, and read by the rider_progress
	// watchdog thread directly from this address (see rider_progress._arm). Static storage,
	// so an address handed to Python stays valid even if the watchdog reads it a moment
	// after the run ended. One slot is enough because GScriptRunning admits one script at a
	// time.
	volatile int32 GCancelRequested = 0;

	// Held at 1 from before the deadline is armed until the instant the script returns, and read
	// by the watchdog from this address (see rider_progress._arm).
	//
	// This is the one signal about the run that does not need the interpreter. The watchdog
	// cannot keep it itself: clearing the Python state needs the game thread to run Python, and
	// that is the thread an abort targets, so an abort can kill the very command that would have
	// stopped it. C++ lowers this flag one instruction after the script returns.
	volatile int32 GScriptInPython = 0;

	// RD terminates a request's lifetime on normal completion, not just on cancellation, so
	// the cancel action has to know whether the request it belongs to is still the one
	// running. Without this, finishing request A would cancel request B.
	std::atomic<uint64> GArmedRequestId{0};
	std::atomic<uint64> GNextRequestId{1};

	// Game-thread only.
	struct FRunState
	{
		double StartSeconds = 0.0;
		double SpentBeforeSeconds = 0.0;
		double LastPumpSeconds = 0.0;
		TUniquePtr<FScopedSlowTask> SlowTask;
		bool bUserCancelled = false;
	};

	FRunState* GRun = nullptr;
	bool GScriptRunning = false;

	// Admits one Python request at a time and publishes it as the cancellable one.
	//
	// Stops a second script starting nested inside the first and overwriting its variables,
	// since batch scripts share __main__. Nesting needs a Slate pump to drain the game-thread
	// task queue; engine slow tasks pump constantly, but whether that drains the queue is
	// unverified. Inert unless it does - only genuine nesting is rejected.
	class FRunGate
	{
	public:
		explicit FRunGate(uint64 RequestId)
			: bEntered(!GScriptRunning)
		{
			check(IsInGameThread());
			if (!bEntered)
				return;
			GScriptRunning = true;
			FPlatformAtomics::InterlockedExchange(&GCancelRequested, 0);
			GArmedRequestId.store(RequestId);
		}

		~FRunGate()
		{
			if (!bEntered)
				return;
			// Clear both flags the constructor set. RD terminates a request's lifetime when the
			// task resolves, and the task resolves inside this scope, so the cancel action can
			// trip GCancelRequested for this very request while the gate is still open. Leaving
			// it set would hand the next run a cancel it never asked for.
			FPlatformAtomics::InterlockedExchange(&GCancelRequested, 0);
			GArmedRequestId.store(0);
			GScriptRunning = false;
		}

		bool IsEntered() const { return bEntered; }

		FRunGate(const FRunGate&) = delete;
		FRunGate& operator=(const FRunGate&) = delete;

	private:
		bool bEntered;
	};
}

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

// Run a Rider-internal snippet in the same scope the user script will use, so that
// rider_progress can snapshot and restore that exact dict.
//
// ExecuteFile, not ExecuteStatement: Py_single_input echoes each statement's value through
// sys.displayhook, which would put a line into LogPython for every call we make.
static bool RunInScope(IPythonScriptPlugin* PythonPlugin, const FString& Command, bool bIsolated)
{
	FPythonCommandEx Cmd;
	Cmd.Command = Command;
	Cmd.ExecutionMode = EPythonCommandExecutionMode::ExecuteFile;
	Cmd.FileExecutionScope = bIsolated
		? EPythonFileExecutionScope::Private
		: EPythonFileExecutionScope::Public;
	return PythonPlugin->ExecPythonCommandEx(Cmd);
}

// Start the deadline and record what the scope held beforehand.
//
// The watchdog lives in Python rather than here so that it works on every engine version
// this plugin supports: raising into the interpreter from C++ would mean taking a
// dependency on the CPython headers, while rider_progress can do it through ctypes.
static void ArmDeadline(IPythonScriptPlugin* PythonPlugin, bool bIsolated, int32 TimeoutMs)
{
	const UPTRINT CancelAddress = reinterpret_cast<UPTRINT>(const_cast<int32*>(&GCancelRequested));
	const UPTRINT RunningAddress = reinterpret_cast<UPTRINT>(const_cast<int32*>(&GScriptInPython));
	const FString Command = FString::Printf(
		TEXT("import rider_progress\n"
			"rider_progress._arm(%d, %llu, %llu)\n"
			"rider_progress._snapshot(globals())\n"),
		TimeoutMs, static_cast<uint64>(CancelAddress), static_cast<uint64>(RunningAddress));
	if (!RunInScope(PythonPlugin, Command, bIsolated))
	{
		// Should not happen normally, but better to log it just in case
		RIDERLINK_LOG(FLogRiderAgentToolsModule, Warning,
			"Could not arm the Python timeout: `import rider_progress` failed.");
	}
}

// Stop the watchdog, and check that it really stopped.
//
// The retry is not paranoia. The watchdog can schedule ScriptAborted in the last instants of a
// run, and CPython then delivers it at the game thread's next bytecode, which belongs to this
// command. The first attempt dies on its `import` line and _disarm() never runs. CPython delivers
// a pending async exception once, so that failed attempt consumes it and the second attempt runs
// on a clean thread. Without the retry the watchdog keeps its state and goes on raising into the
// game thread until the next run arms.
static void DisarmDeadline(IPythonScriptPlugin* PythonPlugin, bool bIsolated)
{
	const FString Command = TEXT("import rider_progress\nrider_progress._disarm()\n");
	if (RunInScope(PythonPlugin, Command, bIsolated))
		return;
	if (RunInScope(PythonPlugin, Command, bIsolated))
		return;
	RIDERLINK_LOG(FLogRiderAgentToolsModule, Warning,
		"Could not stop the Python timeout watchdog. It can abort the next script.");
}

// Drop what an aborted script left in the scope, keeping everything that predates it.
static void RestoreScope(IPythonScriptPlugin* PythonPlugin, bool bIsolated)
{
	RunInScope(PythonPlugin, TEXT("import rider_progress\nrider_progress._restore(globals())\n"), bIsolated);
}

static FString DescribeAbort(int32 ElapsedMs, int32 BudgetMs, bool bUserCancelled, const FString& Traceback)
{
	const TCHAR* Reason = bUserCancelled
		? TEXT("was cancelled from the editor")
		: TEXT("ran out of time");
	return FString::Printf(
		TEXT("Script %s after %d ms (budget %d ms) and was stopped part-way; edits made before "
			"that point are still in place.\nDo less work per call, or pass a larger `timeoutMs`.\n\n%s"),
		Reason, ElapsedMs, BudgetMs, *Traceback);
}

static JetBrains::EditorPlugin::ScriptResult MakeFailure(const FString& Error)
{
	return JetBrains::EditorPlugin::ScriptResult(false, FString(), FString(), Error, false, 0);
}

bool PythonRunContext::ProgressTick(const FString& Message)
{
	// Nothing armed (a script run from the editor's own Python console), or somehow off the
	// game thread: say "keep going" and touch nothing.
	if (!IsInGameThread() || GRun == nullptr)
		return true;

	FRunState& Run = *GRun;
	if (Run.bUserCancelled)
		return false;

	const double Now = FPlatformTime::Seconds();

	if (!Run.SlowTask.IsValid())
	{
		if (Run.SpentBeforeSeconds + (Now - Run.StartSeconds) < PROGRESS_DIALOG_DELAY_SECONDS)
			return true;
		Run.SlowTask = MakeUnique<FScopedSlowTask>(
			0.0f,
			NSLOCTEXT("RiderAgentTools", "PythonScriptRunning", "Running Python script from Rider..."));
		Run.SlowTask->MakeDialog(/*bShowCancelButton=*/true);
	}
	else if (Now - Run.LastPumpSeconds < PROGRESS_PUMP_INTERVAL_SECONDS)
	{
		return true;
	}

	Run.LastPumpSeconds = Now;
	Run.SlowTask->EnterProgressFrame(
		0.0f,
		Message.IsEmpty()
			? NSLOCTEXT("RiderAgentTools", "PythonScriptRunning", "Running Python script from Rider...")
			: FText::FromString(Message));

	if (Run.SlowTask->ShouldCancel())
	{
		Run.bUserCancelled = true;
		return false;
	}
	return true;
}

static JetBrains::EditorPlugin::ScriptResult RunScript(
	IPythonScriptPlugin* PythonPlugin, const FString& Script, bool bIsolated,
	int32 BudgetMs, int32 SpentMs)
{
	using namespace JetBrains::EditorPlugin;

	if (!PythonPlugin || !PythonPlugin->IsPythonAvailable())
	{
		return MakeFailure(TEXT("Python plugin not available"));
	}
	const double StartSeconds = FPlatformTime::Seconds();

	FRunState Run;
	Run.StartSeconds = StartSeconds;
	Run.SpentBeforeSeconds = SpentMs / 1000.0;
	Run.LastPumpSeconds = StartSeconds;
	GRun = &Run;

	// 0 budget means no deadline
	const int32 DeadlineMs = BudgetMs > 0 ? FMath::Max(BudgetMs - SpentMs, 1) : 0;
	const bool bDeadline = DeadlineMs > 0;
	if (bDeadline)
	{
		// Raise the flag before arming, not before the script. _arm starts the watchdog thread
		// and the arm command still has work to do, so the first poll can arrive before the
		// script does. A poll that read 0 there would retire the watchdog and leave the script
		// unbounded, with nothing to report it.
		FPlatformAtomics::InterlockedExchange(&GScriptInPython, 1);
		ArmDeadline(PythonPlugin, bIsolated, DeadlineMs);
	}

	FPythonCommandEx Cmd;
	ConfigureCommand(Cmd, Script, bIsolated);
	const bool bSuccess = PythonPlugin->ExecPythonCommandEx(Cmd);

	if (bDeadline)
	{
		// Lower the flag first, so the watchdog stops before the disarm command runs.
		FPlatformAtomics::InterlockedExchange(&GScriptInPython, 0);
		DisarmDeadline(PythonPlugin, bIsolated);
	}
	// Take the dialog down before anything else gets a chance to pump Slate.
	Run.SlowTask.Reset();
	GRun = nullptr;

	const int32 ElapsedMs = static_cast<int32>((FPlatformTime::Seconds() - StartSeconds) * 1000.0);
	const bool bAborted = !bSuccess && Cmd.CommandResult.Contains(TEXT("rider_progress.ScriptAborted"));

	FString Output = Cmd.LogOutput.Num() > 0
		                 ? CapString(JoinLogOutput(Cmd.LogOutput))
		                 : FString();
	FString Result;
	FString Error;
	SplitResultOrError(Cmd, bSuccess, Result, Error);
	if (bAborted)
	{
		Error = CapString(DescribeAbort(SpentMs + ElapsedMs, BudgetMs, Run.bUserCancelled, Cmd.CommandResult));
		if (!bIsolated)
			RestoreScope(PythonPlugin, bIsolated);
	}

	if (bIsolated)
	{
		ClearPrivateScope(PythonPlugin);
	}
	PostExecGarbageCollect(PythonPlugin);

	return ScriptResult(bSuccess, MoveTemp(Output), MoveTemp(Result), MoveTemp(Error), bAborted, ElapsedMs);
}

// RequestLifetime is the per-call lifetime that RD terminates when the client
// cancels or times out. We check it at two points:
//   1. Before executing Python — skip the work entirely if already cancelled.
//   2. Before Task.set() — calling set() on a cancelled RdTask corrupts the
//      model connection and makes all subsequent calls return "Cancelled" forever.
static void ExecuteOnGameThread(const FString& Script, int32 BudgetMs, uint64 RequestId,
                                rd::Lifetime RequestLifetime, FScriptCallback Callback)
{
	AsyncTask(ENamedThreads::GameThread, [Script, BudgetMs, RequestId, RequestLifetime, Callback = MoveTemp(Callback)]()
	{
		if (RequestLifetime->is_terminated())
			return;
		FRunGate Gate(RequestId);
		if (!Gate.IsEntered())
		{
			Callback(MakeFailure(TEXT("Another Python script is already running in the editor. "
				"Wait for it to finish, or cancel it, before sending another.")));
			return;
		}
		auto* PythonPlugin = IPythonScriptPlugin::Get();
		// Single tool calls always use Private scope: each call gets a fresh __main__ dict,
		// so UObject refs (Blueprints, assets) are released as soon as the script returns.
		// Public scope is only appropriate for batch steps that intentionally share state.
		Callback(RunScript(PythonPlugin, Script, /*bIsolated=*/true, BudgetMs, 0));
	});
}

// Trip the cancel flag when the client abandons the call. The id check matters because RD
// terminates a request's lifetime on normal completion too, so without it a finished
// request would cancel whichever one started next.
static void CancelOnLifetimeEnd(rd::Lifetime RequestLifetime, uint64 RequestId)
{
	// add_action throws on an already-dead lifetime. RD hands us a lifetime it just created,
	// so this only guards against the pathological case.
	if (RequestLifetime->is_terminated())
		return;
	RequestLifetime->add_action([RequestId]
	{
		if (GArmedRequestId.load() == RequestId)
			FPlatformAtomics::InterlockedExchange(&GCancelRequested, 1);
	});
}

void PythonExecutor::BindTo(rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model)
{
	using namespace JetBrains::EditorPlugin;

	Model.get_executeScript().set(
		[](rd::Lifetime RequestLifetime, ScriptRequest const& Request) -> rd::RdTask<ScriptResult>
		{
			rd::RdTask<ScriptResult> Task;
			const uint64 RequestId = GNextRequestId.fetch_add(1);
			CancelOnLifetimeEnd(RequestLifetime, RequestId);
			ExecuteOnGameThread(
				Request.get_script(),
				Request.get_timeoutMs(),
				RequestId,
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
			// One budget for the whole batch, not per script: the editor is frozen for the
			// sum of the steps, so that sum is what needs bounding.
			const int32 BudgetMs = Request.get_timeoutMs();

			if (Scripts.Num() == 0 || StartFrom >= Scripts.Num())
			{
				Task.set(BatchScriptResult(TArray<rd::Wrapper<ScriptResult>>(), StartFrom - 1));
				return Task;
			}

			const uint64 RequestId = GNextRequestId.fetch_add(1);
			CancelOnLifetimeEnd(RequestLifetime, RequestId);

			AsyncTask(ENamedThreads::GameThread,
			          [Scripts, StartFrom, BudgetMs, RequestId, Task, RequestLifetime]() mutable
			          {
				          if (RequestLifetime->is_terminated())
					          return;
				          FRunGate Gate(RequestId);
				          if (!Gate.IsEntered())
				          {
					          TArray<rd::Wrapper<ScriptResult>> Busy;
					          Busy.Emplace(MakeFailure(
						          TEXT("Another Python script is already running in the editor. "
							          "Wait for it to finish, or cancel it, before sending another.")));
					          if (!RequestLifetime->is_terminated())
						          Task.set(BatchScriptResult(MoveTemp(Busy), StartFrom - 1));
					          return;
				          }

				          auto* PythonPlugin = IPythonScriptPlugin::Get();
				          TArray<rd::Wrapper<ScriptResult>> Results;
				          Results.Reserve(Scripts.Num() - StartFrom);
				          int32 LastSuccessful = StartFrom - 1;
				          const double BatchStartSeconds = FPlatformTime::Seconds();

				          for (int32 i = StartFrom; i < Scripts.Num(); ++i)
				          {
					          if (RequestLifetime->is_terminated())
						          return;

					          const int32 SpentMs =
						          static_cast<int32>((FPlatformTime::Seconds() - BatchStartSeconds) * 1000.0);
					          if (BudgetMs > 0 && SpentMs >= BudgetMs)
					          {
						          Results.Emplace(ScriptResult(false, FString(), FString(),
							          TEXT("The batch used up its time budget before this script started, so it "
								          "was skipped. Re-send the remaining scripts with `startFrom`, or raise "
								          "`timeoutMs`."),
							          /*aborted=*/true, 0));
						          break;
					          }
					          // Batch scripts always run as files (multi-statement, shared globals across steps).
					          ScriptResult StepResult =
						          RunScript(PythonPlugin, Scripts[i], /*bIsolated=*/false, BudgetMs, SpentMs);
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
