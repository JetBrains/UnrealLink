#include "PythonExecutor.hpp"
#include "RiderAgentTools.hpp"
#include "IPythonScriptPlugin.h"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"
#include "Async/Async.h"
#include "UObject/GarbageCollection.h"

static const int32 MAX_OUTPUT_CHARS = 10000;

static FString CapString(const FString& Str)
{
    if (Str.Len() <= MAX_OUTPUT_CHARS) return Str;
    const int32 Half = MAX_OUTPUT_CHARS / 2;
    return Str.Left(Half) + TEXT("\n[... output truncated ...]\n") + Str.Right(Half);
}

using FScriptCallback = TFunction<void(JetBrains::EditorPlugin::ScriptResult)>;

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
        const FString Output = Cmd.LogOutput.Num() > 0
            ? CapString(FString::Join(Cmd.LogOutput, TEXT("\n")))
            : FString(TEXT(""));
        const FString Result = CapString(Cmd.CommandResult);
        const FString Error = bSuccess ? FString(TEXT("")) : CapString(Cmd.CommandError);

        CollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS);

        Callback(ScriptResult(bSuccess, FString(Output), FString(Result), FString(Error)));
    });
}

void PythonExecutor::BindTo(rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model)
{
    using namespace JetBrains::EditorPlugin;

    Model.get_executeScript().set(ModelLifetime,
        [](ScriptRequest const& Request) -> rd::RdTask<ScriptResult>
        {
            auto Task = rd::RdTask<ScriptResult>::create();
            ExecuteOnGameThread(
                Request.get_script().data,
                Request.get_isolated(),
                [Task](ScriptResult Result) mutable { Task.set(MoveTemp(Result)); }
            );
            return Task;
        });

    Model.get_executeBatchScripts().set(ModelLifetime,
        [](BatchScriptRequest const& Request) -> rd::RdTask<BatchScriptResult>
        {
            auto Task = rd::RdTask<BatchScriptResult>::create();
            const auto Scripts = Request.get_scripts();
            const int32 StartFrom = Request.get_startFrom();

            if (Scripts.empty() || StartFrom >= static_cast<int32>(Scripts.size()))
            {
                Task.set(BatchScriptResult({}, StartFrom - 1));
                return Task;
            }

            AsyncTask(ENamedThreads::GameThread,
                [Scripts, StartFrom, Task]() mutable
                {
                    auto* PythonPlugin = IPythonScriptPlugin::Get();
                    std::vector<ScriptResult> Results;
                    int32 LastSuccessful = StartFrom - 1;

                    for (int32 i = StartFrom; i < static_cast<int32>(Scripts.size()); ++i)
                    {
                        FPythonCommandEx Cmd;
                        Cmd.Command = Scripts[i].data;
                        Cmd.ExecutionMode = EPythonCommandExecutionMode::ExecuteStatement;

                        bool bSuccess = PythonPlugin && PythonPlugin->IsPythonAvailable()
                            && PythonPlugin->ExecPythonCommandEx(Cmd);

                        const FString Output = Cmd.LogOutput.Num() > 0
                            ? CapString(FString::Join(Cmd.LogOutput, TEXT("\n")))
                            : FString(TEXT(""));
                        const FString Result = CapString(Cmd.CommandResult);
                        const FString Error = bSuccess ? FString(TEXT("")) : CapString(Cmd.CommandError);

                        CollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS);

                        Results.emplace_back(bSuccess, FString(Output), FString(Result), FString(Error));

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
