using System.Collections.Generic;
using System.Linq;
using JetBrains.Application.BuildScript.PreCompile.Autofix;
using JetBrains.Application.BuildScript.Solution;
using JetBrains.Build;

namespace JetBrains.ReSharper.Plugins.UnrealLink.BuildScript
{
	public static class DefineUnrealLinkConstants
	{
		[BuildStep]
		public static IEnumerable<AutofixAllowedDefineConstant> YieldAllowedDefineConstantsForUnrealLink()
		{
			var constants = new List<string>();

			constants.AddRange(new[] {"$(DefineConstants)", "RIDER"});

			return constants.SelectMany(s => new []
			{
				new AutofixAllowedDefineConstant(new SubplatformName("Plugins\\UnrealLink\\src\\dotnet"), s)
            });
		}
	}
}