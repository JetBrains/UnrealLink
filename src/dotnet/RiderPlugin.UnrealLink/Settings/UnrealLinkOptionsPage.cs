using System.Linq;
using JetBrains.Application.Settings;
using JetBrains.Application.UI.Options;
using JetBrains.Application.UI.Options.OptionsDialog;
using JetBrains.IDE.UI.Extensions;
using JetBrains.IDE.UI.Options;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Psi.Cpp.Resources;
using JetBrains.ReSharper.Feature.Services.OptionPages.CodeEditing;
using JetBrains.ReSharper.Resources.Settings;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Model.UIAutomation;
using RiderPlugin.UnrealLink.Model.FrontendBackend;
using RiderPlugin.UnrealLink.PluginInstaller;
using RiderPlugin.UnrealLink.Resources;

namespace RiderPlugin.UnrealLink.Settings
{
    [SettingsKey(typeof(CodeEditingSettings),
        typeof(Strings),
        nameof(Strings.UnrealLinkPluginSettings_Text))]
    public class UnrealLinkSettings
    {
        [SettingsEntry(false,
            typeof(Strings),
            nameof(Strings.IfThisOptionIsEnabledTheRiderLinkEditor_Text))]
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

            var installInEngineButton = AddButton(Strings.InstallRiderLinkInEngine_Text, () =>
            {
                unrealPluginInstaller?.HandleManualInstallPlugin(
                    new InstallPluginDescription(PluginInstallLocation.Engine, ForceInstall.Yes)
                    );
            });
            AddCommentText(Strings.InstallRiderLinkPluginInEngineDescription_Text);

            var installInGameButton = AddButton(Strings.InstallRiderLinkInGame_Text, () =>
            {
                unrealPluginInstaller?.HandleManualInstallPlugin(
                    new InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes)
                    );
            });
            AddCommentText(Strings.InstallRiderLinkPluginInGameDescription_Text);

            var extractInEngineButton = AddButton(Strings.ExtractRiderLinkInEngine_Text, () =>
            {
                unrealPluginInstaller?.HandleManualInstallPlugin(
                    new InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes, false)
                );
            });
            AddCommentText(Strings.ExtractRiderLinkPluginInEngineDescription_Text);

            var extractInGameButton = AddButton(Strings.ExtractRiderLinkInGame_Text, () =>
            {
                unrealPluginInstaller?.HandleManualInstallPlugin(
                    new InstallPluginDescription(PluginInstallLocation.Game, ForceInstall.Yes, false)
                );
            });
            AddCommentText(Strings.ExtractRiderLinkPluginInGameDescription_Text);

            var installationInProgressText = AddText(Strings.RiderLinkInstallationIsInProgress_Text);
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