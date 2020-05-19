using System.Linq;
using JetBrains.Application.Settings;
using JetBrains.Application.UI.Options;
using JetBrains.Application.UI.Options.OptionsDialog;
using JetBrains.DataFlow;
using JetBrains.IDE.UI.Extensions;
using JetBrains.IDE.UI.Options;
using JetBrains.Lifetimes;
using JetBrains.Platform.RdFramework.Util;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Cpp.Options;
using JetBrains.ReSharper.Feature.Services.OptionPages.CodeEditing;
using JetBrains.ReSharper.Resources.Settings;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Model;
using JetBrains.Rider.Model.UIAutomation;
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
            
            SetupInstallButtons();
        }

        private void SetupInstallButtons()
        {
            var installInEngineButton = AddButton("Install RiderLink plugin in Engine", () =>
            {
                var owner = Shell.Instance.GetComponents<SolutionManagerBase>()
                    .FirstOrDefault(it => it.IsRealSolutionOwner && it.CurrentSolution != null);
                var solution = owner?.CurrentSolution;

                var unrealPluginInstaller = solution?.GetComponent<UnrealPluginInstaller>();
                unrealPluginInstaller?.HandleManualInstallPlugin(PluginInstallLocation.Engine);
            });
            foreach (var beControl in installInEngineButton.Descendants<BeControl>())
            {
                installInEngineButton.Enabled.FlowIntoRd(Lifetime, beControl.Enabled);
            }

            var installInGameButton = AddButton("Install RiderLink plugin in Game", () =>
            {
                var owner = Shell.Instance.GetComponents<SolutionManagerBase>()
                    .FirstOrDefault(it => it.IsRealSolutionOwner && it.CurrentSolution != null);
                var solution = owner?.CurrentSolution;

                var unrealPluginInstaller = solution?.GetComponent<UnrealPluginInstaller>();
                unrealPluginInstaller?.HandleManualInstallPlugin(PluginInstallLocation.Game);
            });
            foreach (var beControl in installInGameButton.Descendants<BeControl>())
            {
                installInGameButton.Enabled.FlowIntoRd(Lifetime, beControl.Enabled);
            }

            var installationInProgressText = AddText("Installation is in progress...");
            installationInProgressText.Visible.Value = ControlVisibility.Hidden;
            
            var owner = Shell.Instance.GetComponents<SolutionManagerBase>()
                .FirstOrDefault(it => it.IsRealSolutionOwner && it.CurrentSolution != null);
            var solution = owner?.CurrentSolution;

            var unrealPluginInstaller = solution?.GetComponent<UnrealPluginInstaller>();
            unrealPluginInstaller?.InstallationIsInProgress.Change.Advise_HasNew(unrealPluginInstaller.Lifetime,
                installationInProgress =>
                {
                    installInEngineButton.Enabled.Value = !installationInProgress.New;
                    installInGameButton.Enabled.Value = !installationInProgress.New;
                    installationInProgressText.Visible.Value = installationInProgress.New ?
                        ControlVisibility.Visible :
                        ControlVisibility.Hidden;
                }
            );
        }
    }
}