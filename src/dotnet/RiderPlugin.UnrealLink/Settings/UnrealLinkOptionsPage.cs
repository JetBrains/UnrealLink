using System.Linq;
using JetBrains.Application.Help;
using JetBrains.Application.Settings;
using JetBrains.Application.UI.Controls.FileSystem;
using JetBrains.Application.UI.Options;
using JetBrains.Application.UI.Options.OptionsDialog;
using JetBrains.Application.UI.Options.OptionsDialog.SimpleOptions.ViewModel;
using JetBrains.DataFlow;
using JetBrains.HabitatDetector;
using JetBrains.IDE.UI;
using JetBrains.IDE.UI.Extensions;
using JetBrains.IDE.UI.Extensions.PathActions;
using JetBrains.IDE.UI.Options;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Psi.Cpp.Resources;
using JetBrains.ReSharper.Feature.Services.OptionPages.CodeEditing;
using JetBrains.ReSharper.Resources.Settings;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Model.UIAutomation;
using JetBrains.Util;
using RiderPlugin.UnrealLink.Model.FrontendBackend;
using RiderPlugin.UnrealLink.PluginInstaller;
using RiderPlugin.UnrealLink.Resources;
using Strings = RiderPlugin.UnrealLink.Resources.Strings;

namespace RiderPlugin.UnrealLink.Settings
{
    public enum InstallOrExtract
    {
        Install,
        Extract
    }
    [SettingsKey(typeof(CodeEditingSettings),
        typeof(Strings),
        nameof(Strings.UnrealLinkPluginSettings_Text))]
    public class UnrealLinkSettings
    {
        [SettingsEntry(false,
            typeof(Strings),
            nameof(Strings.IfThisOptionIsEnabledTheRiderLinkEditor_Text))]
        public bool AutoUpdateRiderLinkPlugin;

        [SettingsEntry(null,
            typeof(Strings),
            nameof(Strings.IntermediateBuildFolderRoot_Text))]
        public FileSystemPath IntermediateBuildFolderRoot;

        [SettingsEntry(InstallOrExtract.Install,
            typeof(Strings),
            nameof(Strings.DefaultBehaviorForRiderLinkUpdate_Text))]
        public InstallOrExtract DefaultUpdateRiderLinkBehavior;

        [SettingsEntry(PluginInstallLocation.Engine,
            typeof(Strings),
            nameof(Strings.DefaultLocationForRiderLinkInstallation_Text))]
        public InstallOrExtract DefaultLocationForRiderLink;
    }
    

    [OptionsPage(PID, Name, typeof(CppThemedIcons.Unreal), Sequence = 0.02,
        ParentId = CodeEditingPage.PID, SearchTags = new []{"Unreal Engine", "UnrealLink", "RiderLink"},
        NameResourceType = typeof(Strings),
        HelpKeyword = HelpId.Settings_Languages_Unreal_Engine,
        NameResourceName = nameof(Strings.UnrealLinkPluginSettings_Title_Text))]
    public class UnrealLinkOptionsPage : BeSimpleOptionsPage
    {
      // Keep these in sync with the values in the front end!
        public const string PID = "UnrealLinkOptions";
        public const string Name = "Unreal Engine";

        public UnrealLinkOptionsPage(
            Lifetime lifetime,
            OptionsPageContext optionsPageContext,
            OptionsSettingsSmartContext optionsSettingsSmartContext,
            IconHostBase iconHost,
            ICommonFileDialogs commonFileDialogs)
            : base(lifetime, optionsPageContext, optionsSettingsSmartContext)
        {
            AddTmpDirChooserOption(lifetime, iconHost, commonFileDialogs);
            AddAutoUpdateOption(lifetime);
            SetupInstallButtons();
        }

        private void AddAutoUpdateOption(Lifetime lifetime)
        {
            AddHeader(Strings.Settings_AutoUpdate_Header_Text);
            var autoUpdateCheckbox = AddBoolOption((UnrealLinkSettings k) => k.AutoUpdateRiderLinkPlugin,
                Strings.AutoupdateRiderLink_CheckBox_Text);
            using (Indent())
            {
                AddRadioOption(
                    (UnrealLinkSettings s) => s.DefaultUpdateRiderLinkBehavior,
                    /*Localized*/ string.Empty,
                    new RadioOptionPoint(InstallOrExtract.Install, Strings.BuildAndInstall_RadioButton_Text),
                    new RadioOptionPoint(InstallOrExtract.Extract, Strings.ExtractOnly_RadioButton_Text));
                AddCommentText(Strings.InstallOrExtractRadio_Comment_Text);
                AddRadioOption(
                    (UnrealLinkSettings s) => s.DefaultLocationForRiderLink,
                    /*Localized*/ string.Empty,
                    new RadioOptionPoint(PluginInstallLocation.Engine, Strings.InstallInEngine_Text),
                    new RadioOptionPoint(PluginInstallLocation.Game, Strings.InstallInGame_Text));

            }
        }

        private void AddTmpDirChooserOption(Lifetime lifetime, IconHostBase iconHost, ICommonFileDialogs commonFileDialogs)
        {
            AddHeader(Strings.Settings_General_Header_Text);
            var intermediateBuildFolderProperty = new Property<string>( "IntermediateBuildFolderProperty");
      
            var intermediateBuildFolder = 
                OptionsSettingsSmartContext.GetValue((UnrealLinkSettings s) => s.IntermediateBuildFolderRoot);

            var defaultBuildPath = VirtualFileSystemDefinition.GetTempPath(InteractionContext.SolutionContext);
            intermediateBuildFolderProperty.Value = intermediateBuildFolder.IsNullOrEmpty() ? defaultBuildPath.FullPath : intermediateBuildFolder.FullPath;
      
            intermediateBuildFolderProperty.Change.Advise_NoAcknowledgement(lifetime, args =>
            {
                var newValue = FileSystemPath.Parse(args.New);
                
                OptionsSettingsSmartContext.SetValue((UnrealLinkSettings s) => s.IntermediateBuildFolderRoot, (newValue.IsValidOnCurrentOS && newValue.IsAbsolute)? newValue : defaultBuildPath.ToNativeFileSystemPath());
            });

            AddControl(Strings.IntermediateBuildFolderRoot_Text.GetBeLabel());
            AddFolderChooserOption(
                intermediateBuildFolderProperty,
                defaultBuildPath.ToNativeFileSystemPath(),
                defaultBuildPath.ToNativeFileSystemPath(),
                iconHost,
                commonFileDialogs,
                null,
                null,
                new[] { (BeSimplePathValidationRules.SHOULD_BE_ABSOLUTE, ValidationStates.validationWarning) },
                path => CheckForNonAsciiSymbols(path));
      
            AddCommentText(Strings.BuildingRiderLinkMightFailWithNonASCIISymbols_Text);
        }

        private (string, ValidationStates) CheckForNonAsciiSymbols(FileSystemPath path)
        {
            if (PlatformUtil.RuntimePlatform == JetPlatform.Windows && path.FullPath.Any(c => c >= 128))
            {
                return (Strings.PathContainsNonASCIICharactersBuild_Text, ValidationStates.validationWarning);
            }
            
            return ("", ValidationStates.validationPassed);
        }

        private void SetupInstallButtons()
        {
            AddHeader(Strings.Settings_ManualInstallation_Header_Text);
            var owner = Shell.Instance.GetComponents<RealSolutionManagerBase>()
                .FirstOrDefault(it => it.CurrentSolution != null);
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