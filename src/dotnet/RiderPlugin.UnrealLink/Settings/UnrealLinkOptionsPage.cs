using System.Linq;
using JetBrains.Application.Settings;
using JetBrains.Application.UI.Options;
using JetBrains.Application.UI.Options.OptionsDialog;
using JetBrains.IDE.UI.Options;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Cpp.Options;
using JetBrains.ReSharper.Feature.Services.OptionPages.CodeEditing;
using JetBrains.ReSharper.Resources.Settings;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Model;
using RiderPlugin.UnrealLink.PluginInstaller;

namespace RiderPlugin.UnrealLink.Settings
{
    [SettingsKey(typeof(CodeEditingSettings), "UnrealLink plugin settings")]
    public class UnrealLinkSettings
    {
        [SettingsEntry(false,
            "If this option is enabled, the RiderLink editor plugin will be automatically installed and updated.")]
        public bool InstallRiderLinkPlugin;
    }

    [OptionsPage(PID, Name, typeof(CppThemedIcons.Unreal), Sequence = 0.02,
        ParentId = CodeEditingPage.PID, SearchTags = new []{"Unreal Engine", "UnrealLink", "RiderLink"})]
    public class UnrealLinkOptionsPage : BeSimpleOptionsPage
    {
        // Keep these in sync with the values in the front end!
        public const string PID = "UnrealLinkOptions";
        public const string Name = "Unreal Engine";

        public UnrealLinkOptionsPage(Lifetime lifetime,
            OptionsPageContext optionsPageContext,
            OptionsSettingsSmartContext optionsSettingsSmartContext)
            : base(lifetime, optionsPageContext, optionsSettingsSmartContext)
        {
            AddBoolOption((UnrealLinkSettings k) => k.InstallRiderLinkPlugin,
                "Automatically install and update Rider's UnrealLink editor plugin (recommended)");
            AddButton("Install RiderLink plugin in Engine", () =>
            {
                var owner = Shell.Instance.GetComponents<SolutionManagerBase>()
                    .FirstOrDefault(it => it.IsRealSolutionOwner && it.CurrentSolution != null);
                var solution = owner?.CurrentSolution;

                var unrealPluginInstaller = solution?.GetComponent<UnrealPluginInstaller>();
                unrealPluginInstaller?.HandleManualInstallPlugin(PluginInstallLocation.Engine);
            });
            AddButton("Install RiderLink plugin in Game", () => {
                var owner = Shell.Instance.GetComponents<SolutionManagerBase>()
                    .FirstOrDefault(it => it.IsRealSolutionOwner && it.CurrentSolution != null);
                var solution = owner?.CurrentSolution;

                var unrealPluginInstaller = solution?.GetComponent<UnrealPluginInstaller>();
                unrealPluginInstaller?.HandleManualInstallPlugin(PluginInstallLocation.Game);});
        }
    }
}