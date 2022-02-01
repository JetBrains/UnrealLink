// Fill out your copyright notice in the Description page of Project Settings.


#include "RDRunTestsCommandlet.h"

#include "ShaderCompiler.h"
#include "Framework/Application/SlateApplication.h"
#include "Misc/AutomationTest.h"
#include "Misc/FileHelper.h"
#include "Modules/ModuleManager.h"
#include "RDCmdlets/RDCmdletsMisc.h"
#include "SlateNullRenderer/Public/Interfaces/ISlateNullRendererModule.h"

URDRunTestsCommandlet::URDRunTestsCommandlet()
{
	IsClient = false;
	IsEditor = false;
	IsServer = false;
	LogToConsole = true;
}

int32 URDRunTestsCommandlet::Main(const FString& Params)
{
	FSlateApplication::Create();
	TSharedPtr<FSlateRenderer> SlateRenderer = FModuleManager::Get().LoadModuleChecked<ISlateNullRendererModule>("SlateNullRenderer").CreateSlateNullRenderer();
	FSlateApplication::Get().InitializeRenderer(SlateRenderer.ToSharedRef());

	FString Filter;
	FParse::Value(*Params, TEXT("Tests="), Filter);

	FString OutFileName = FPaths::ProjectSavedDir() + TEXT("run-tests-report.xml");
	FParse::Value(*Params, TEXT("File="), OutFileName);

	TArray<FAutomationTestInfo> TestInfo;

	FAutomationTestFramework& TestFramework = FAutomationTestFramework::GetInstance();
	// TestFramework.SetRequestedTestFilter(EAutomationTestFlags::ProductFilter);
	TestFramework.GetValidTestNames(TestInfo);
	TArray<FString> TestsOutput;
	int32 NumTests = 0;
	int32 NumFailures = 0;
	for (FAutomationTestInfo& Test : TestInfo)
	{
		if (Filter.Len() > 0 && !Test.GetDisplayName().Contains(Filter))
		{
			continue;
		}

		++NumTests;

		GLog->Logf(ELogVerbosity::Log, TEXT("TEST_START[%s]"), *Test.GetTestName());

		if (GShaderCompilingManager)
		{
			GShaderCompilingManager->FinishAllCompilation();
		}
		TestFramework.StartTestByName(Test.GetTestName(), 0);

		while (!FAutomationTestFramework::Get().ExecuteLatentCommands())
		{
			// Nothing
		}

		FAutomationTestExecutionInfo TestExecutionInfo;
		if (!TestFramework.StopTest(TestExecutionInfo))
		{
			++NumFailures;
		}

		GLog->Logf(ELogVerbosity::Log, TEXT("TEST_END[%s]: %s. ELAPSED: %f"), *Test.GetTestName(), TestExecutionInfo.bSuccessful ? TEXT("SUCCESS") : TEXT("FAILURE"), TestExecutionInfo.Duration);

		AppendTestExecutionInfo(Test, TestExecutionInfo, TestsOutput);

		// The renderer can't release resources allocated by the last test until 3 frames has passed
		ENQUEUE_RENDER_COMMAND(WaitThreeFrames)
		([](auto& RHICmdList) {
			for (int i = 0; i < 3; ++i)
			{
				RHICmdList.EndFrame();
			}
		});
	}

	FSlateApplication::Shutdown();

	// See GitLab JUnit parser: https://gitlab.com/gitlab-org/gitlab/-/blob/master/lib/gitlab/ci/parsers/test/junit.rb
	TArray<FString> JUnitReport;
	JUnitReport.Add(TEXT("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
	JUnitReport.Add(FString::Printf(TEXT("<testsuite name=\"ue4\" errors=\"\" failures=\"%d\" tests=\"%d\" skipped=\"0\" xmlns=\"http://junit.org\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://junit.org https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd\">"),
	                                NumFailures,
	                                NumTests));
	JUnitReport.Append(TestsOutput);
	JUnitReport.Add(TEXT("</testsuite>"));

	return FFileHelper::SaveStringArrayToFile(JUnitReport, *OutFileName) && NumFailures == 0 ? 0 : 1;
}


FString URDRunTestsCommandlet::EscapeXmlAttr(const FString& Value)
{
	// See https://stackoverflow.com/a/32588697
	return Value
		.Replace(TEXT("&"), TEXT("&amp;"))
		.Replace(TEXT("\""), TEXT("&quot;"))
		.Replace(TEXT("<"), TEXT("&lt;"));
}

FString URDRunTestsCommandlet::EscapeXmlCData(const FString& Value)
{
	FString Result = TEXT("<![CDATA[");
	// See https://stackoverflow.com/a/223773
	Result += Value.Replace(TEXT("]]>"), TEXT("]]]]><![CDATA[>"));
	Result += TEXT("]]>");
	return Result;
}

void URDRunTestsCommandlet::AppendTestExecutionInfo(const FAutomationTestInfo& TestInfo, const FAutomationTestExecutionInfo& ExecutionInfo, TArray<FString>& JUnitReport)
{
	const FString RootDir = FPlatformMisc::GetEnvironmentVariable(TEXT("CI_PROJECT_DIR"));
	FString SourceFile = TestInfo.GetSourceFile();
	// We want to build a path that is relative to VCS root
	// so that GitLab CI would produce clickable links
	// See https://gitlab.com/gitlab-org/gitlab/-/merge_requests/53650
	SourceFile.RemoveFromStart(RootDir);
	SourceFile.ReplaceInline(TEXT("\\"), TEXT("/"));

	JUnitReport.Add(FString::Printf(
		TEXT("<testcase classname=\"%s\" name=\"%s\" time=\"%s\" file=\"%s\" line=\"%d\">"),
		*EscapeXmlAttr(TestInfo.GetTestName()),
		*EscapeXmlAttr(TestInfo.GetDisplayName()),
		*EscapeXmlAttr(FString::SanitizeFloat(ExecutionInfo.Duration)),
		*EscapeXmlAttr(SourceFile),
		TestInfo.GetSourceFileLine()));

	if (!ExecutionInfo.bSuccessful)
	{
		JUnitReport.Add(TEXT("<failure type=\"\">"));

		for (const FAutomationExecutionEntry& Entry : ExecutionInfo.GetEntries())
		{
			JUnitReport.Add(EscapeXmlCData(Entry.Event.Message));
		}

		JUnitReport.Add(TEXT("</failure>"));
	}

	JUnitReport.Add(TEXT("</testcase>"));
}