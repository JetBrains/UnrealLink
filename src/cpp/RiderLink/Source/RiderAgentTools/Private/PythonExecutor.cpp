#include "PythonExecutor.hpp"
#include "RiderAgentTools.hpp"
#include "IPythonScriptPlugin.h"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"
#include "Async/Async.h"
#include "UObject/GarbageCollection.h"

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
        Cmd.Command = Script;
        Cmd.ExecutionMode = bIsolated
            ? EPythonCommandExecutionMode::EvaluateStatement
            : EPythonCommandExecutionMode::ExecuteStatement;

        const bool bSuccess = PythonPlugin->ExecPythonCommandEx(Cmd);
        FString Output = Cmd.LogOutput.Num() > 0
            ? CapString(JoinLogOutput(Cmd.LogOutput))
            : FString(TEXT(""));
        FString Result;
        FString Error;
        SplitResultOrError(Cmd, bSuccess, Result, Error);

        CollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS);

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
                        Cmd.Command = Scripts[i];
                        Cmd.ExecutionMode = EPythonCommandExecutionMode::ExecuteStatement;

                        const bool bSuccess = PythonPlugin && PythonPlugin->IsPythonAvailable()
                            && PythonPlugin->ExecPythonCommandEx(Cmd);

                        FString Output = Cmd.LogOutput.Num() > 0
                            ? CapString(JoinLogOutput(Cmd.LogOutput))
                            : FString(TEXT(""));
                        FString Result;
                        FString Error;
                        SplitResultOrError(Cmd, bSuccess, Result, Error);

                        CollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS);

                        Results.Emplace(ScriptResult(bSuccess, MoveTemp(Output), MoveTemp(Result), MoveTemp(Error)));

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
