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
using RiderPlugin.UnrealLink.Model.FrontendBackend;
using RiderPlugin.UnrealLink.PluginInstaller;

namespace RiderPlugin.UnrealLink.Settings
{
    [SettingsKey(typeof(CodeEditingSettings), "UnrealLink plugin settings")]
    public class UnrealLinkSettings
    {
        [SettingsEntry(false,
            "If this option is enabled, the RiderLink editor plugin will be automatically updated.")]
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
                "Automatically update RiderLink plugin for Unreal Editor (recommended)");
            
            SetupInstallButtons();
        }

        private void SetupInstallButtons()
        {
            var owner = Shell.Instance.GetComponents<SolutionManagerBase>()
                .FirstOrDefault(it => it.IsRealSolutionOwner && it.CurrentSolution != null);
            var solution = owner?.CurrentSolution;
            var unrealPluginInstaller = solution?.GetComponent<UnrealPluginInstaller>();

            var installInEngineButton = AddButton("Install RiderLink in Engine", () =>
            {
                unrealPluginInstaller?.HandleManualInstallPlugin(
                    new InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.Yes)
                    );
            });
            AddCommentText("Install RiderLink plugin into the Engine. Doesn't work with UE5 from Epic Games Launcher.");

            var installInGameButton = AddButton("Install RiderLink in Game", () =>
            {
                unrealPluginInstaller?.HandleManualInstallPlugin(
                    new InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes)
                    );
            });
            AddCommentText("Install RiderLink plugin into every Game available in the Solution. " +
                           "If Rider will fail to install RiderLink plugin to any of the Game projects, It'll revert the installation process.");

            var extractInEngineButton = AddButton("Extract RiderLink in Engine", () =>
            {
                unrealPluginInstaller?.HandleManualInstallPlugin(
                    new InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes, false)
                );
            });
            AddCommentText("Extract RiderLink plugin into the Engine in the Solution.\n" +
                           "Note: Use it for Unreal Engine version built from source only. It will not work with UE4 and UE5 from Epic Games Launcher.\n" +
                           "Use this option if installation of RiderLink plugin fails for some reason.");

            var extractInGameButton = AddButton("Extract RiderLink in Game", () =>
            {
                unrealPluginInstaller?.HandleManualInstallPlugin(
                    new InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes, false)
                );
            });
            AddCommentText("Extract RiderLink plugin into every Game available in the Solution.\n" +
                           "Note: It will not try to build RiderLink for your current project.\n" +
                           "Use this option if installation of RiderLink plugin fails for some reason.");

            var installationInProgressText = AddText("Installation is in progress...");
            installationInProgressText.Visible.Value = ControlVisibility.Hidden;

            var unrealHost = solution?.GetComponent<UnrealHost>();
            unrealHost?.myModel.RiderLinkInstallationInProgress.Advise(unrealPluginInstaller.Lifetime,
                installationInProgress =>
                {
                    installInEngineButton.Enabled.Value = !installationInProgress;
                    foreach (var beControl in installInEngineButton.Descendants<BeControl>())
                    {
                        beControl.Enabled.Value = !installationInProgress;
                    }
                    installInGameButton.Enabled.Value = !installationInProgress;
                    foreach (var beControl in installInGameButton.Descendants<BeControl>())
                    {
                        beControl.Enabled.Value = !installationInProgress;
                    }
                    extractInEngineButton.Enabled.Value = !installationInProgress;
                    foreach (var beControl in extractInEngineButton.Descendants<BeControl>())
                    {
                        beControl.Enabled.Value = !installationInProgress;
                    }
                    extractInGameButton.Enabled.Value = !installationInProgress;
                    foreach (var beControl in extractInGameButton.Descendants<BeControl>())
                    {
                        beControl.Enabled.Value = !installationInProgress;
                    }
                    installationInProgressText.Visible.Value = installationInProgress ?
                        ControlVisibility.Visible :
                        ControlVisibility.Hidden;
                }
            );
        }
    }
}