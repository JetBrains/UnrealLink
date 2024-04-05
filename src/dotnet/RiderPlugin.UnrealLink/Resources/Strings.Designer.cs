namespace RiderPlugin.UnrealLink.Resources
{
  using System;
  using JetBrains.Application.I18n;
  using JetBrains.DataFlow;
  using JetBrains.Diagnostics;
  using JetBrains.Lifetimes;
  using JetBrains.Util;
  using JetBrains.Util.Logging;
  using JetBrains.Application.I18n.Plurals;
  
  [global::System.Diagnostics.DebuggerNonUserCodeAttribute()]
  [global::System.Runtime.CompilerServices.CompilerGeneratedAttribute()]
  public static class Strings
  {
    private static readonly ILogger ourLog = Logger.GetLogger("RiderPlugin.UnrealLink.Resources.Strings");

    static Strings()
    {
      CultureContextComponent.Instance.WhenNotNull(Lifetime.Eternal, (lifetime, instance) =>
      {
        lifetime.Bracket(() =>
          {
            ourResourceManager = new Lazy<JetResourceManager>(
              () =>
              {
                return instance
                  .CreateResourceManager("RiderPlugin.UnrealLink.Resources.Strings", typeof(Strings).Assembly);
              });
          },
          () =>
          {
            ourResourceManager = null;
          });
      });
    }
    
    private static Lazy<JetResourceManager> ourResourceManager = null;
    
    [global::System.ComponentModel.EditorBrowsableAttribute(global::System.ComponentModel.EditorBrowsableState.Advanced)]
    public static JetResourceManager ResourceManager
    {
      get
      {
        var resourceManager = ourResourceManager;
        if (resourceManager == null)
        {
          return ErrorJetResourceManager.Instance;
        }
        return resourceManager.Value;
      }
    }

    public static string Choice(string format, params object[] args)
    {
        var formatter = ResourceManager.ChoiceFormatter;
        if (formatter == null) return "???";
        return string.Format(formatter, format, args);
    }

    public static string DeletingRiderLinkPlugin_Text => ResourceManager.GetString("DeletingRiderLinkPlugin_Text");
    public static string FailedToBuildRiderLinkPlugin_Text => ResourceManager.GetString("FailedToBuildRiderLinkPlugin_Text");
    public static string FailedToDeleteRiderLink_Text => ResourceManager.GetString("FailedToDeleteRiderLink_Text");
    public static string IfThisOptionIsEnabledTheRiderLinkEditor_Text => ResourceManager.GetString("IfThisOptionIsEnabledTheRiderLinkEditor_Text");
    public static string RiderLinkIsDeleted_Text => ResourceManager.GetString("RiderLinkIsDeleted_Text");
    public static string UnrealLinkPluginSettings_Text => ResourceManager.GetString("UnrealLinkPluginSettings_Text");
    public static string UnrealLinkPluginSettings_Title_Text => ResourceManager.GetString("UnrealLinkPluginSettings_Title_Text");
    public static string InstallRiderLinkInEngine_Text => ResourceManager.GetString("InstallRiderLinkInEngine_Text");
    public static string InstallRiderLinkPluginInEngineDescription_Text => ResourceManager.GetString("InstallRiderLinkPluginInEngineDescription_Text");
    public static string InstallRiderLinkInGame_Text => ResourceManager.GetString("InstallRiderLinkInGame_Text");
    public static string InstallRiderLinkPluginInGameDescription_Text => ResourceManager.GetString("InstallRiderLinkPluginInGameDescription_Text");
    public static string ExtractRiderLinkInEngine_Text => ResourceManager.GetString("ExtractRiderLinkInEngine_Text");
    public static string ExtractRiderLinkPluginInEngineDescription_Text => ResourceManager.GetString("ExtractRiderLinkPluginInEngineDescription_Text");
    public static string ExtractRiderLinkInGame_Text => ResourceManager.GetString("ExtractRiderLinkInGame_Text");
    public static string ExtractRiderLinkPluginInGameDescription_Text => ResourceManager.GetString("ExtractRiderLinkPluginInGameDescription_Text");
    public static string RiderLinkInstallationIsInProgress_Text => ResourceManager.GetString("RiderLinkInstallationIsInProgress_Text");
    public static string UnrealEngine_Version_IsRequired_Title => ResourceManager.GetString("UnrealEngine_Version_IsRequired_Title");
    public static string UnrealEngine_Version_IsRequired_Message => ResourceManager.GetString("UnrealEngine_Version_IsRequired_Message");
    public static string RiderLinkPluginExtracted_Title => ResourceManager.GetString("RiderLinkPluginExtracted_Title");
    public static string RiderLinkPluginExtracted_Message => ResourceManager.GetString("RiderLinkPluginExtracted_Message");
    public static string RiderLinkPluginInstalled_Title => ResourceManager.GetString("RiderLinkPluginInstalled_Title");
    public static string RiderLinkPluginInstalled_Message => ResourceManager.GetString("RiderLinkPluginInstalled_Message");
    public static string RefreshProjectsAfterRiderLinkInstallation_Text => ResourceManager.GetString("RefreshProjectsAfterRiderLinkInstallation_Text");
    public static string FailedToPatchRiderLinkUplugin_Text => ResourceManager.GetString("FailedToPatchRiderLinkUplugin_Text");
    public static string FailedToPatchRiderLinkUplugin_Message => ResourceManager.GetString("FailedToPatchRiderLinkUplugin_Message");
    public static string RefreshingProjectFiles_Text => ResourceManager.GetString("RefreshingProjectFiles_Text");
    public static string RiderLinkInstallationHasBeenCancelled_Text => ResourceManager.GetString("RiderLinkInstallationHasBeenCancelled_Text");
    public static string FailedToBuildRiderLinkPluginFor__Text => ResourceManager.GetString("FailedToBuildRiderLinkPluginFor__Text");
    public static string Reason_UatIsNotAvailable_Text => ResourceManager.GetString("Reason_UatIsNotAvailable_Text");
    public static string RefreshingProjectsFilesHasBeenCancelled_Text => ResourceManager.GetString("RefreshingProjectsFilesHasBeenCancelled_Text");
    public static string FailedToRefreshProjectFiles_Text => ResourceManager.GetString("FailedToRefreshProjectFiles_Text");
    public static string RiderLinkPluginWillNotBeVisibleInThe_Text => ResourceManager.GetString("RiderLinkPluginWillNotBeVisibleInThe_Text");
    public static string NeedToRefreshProjectFilesManually_Text => ResourceManager.GetString("NeedToRefreshProjectFilesManually_Text");
    public static string IntermediateBuildFolderRoot_Text => ResourceManager.GetString("IntermediateBuildFolderRoot_Text");
    public static string IntermediateBuildFolder_FileChooserOption_Tooltip => ResourceManager.GetString("IntermediateBuildFolder_FileChooserOption_Tooltip");
    public static string BuildingRiderLinkMightFailWithNonASCIISymbols_Text => ResourceManager.GetString("BuildingRiderLinkMightFailWithNonASCIISymbols_Text");
    public static string DefaultBehaviorForRiderLinkUpdate_Text => ResourceManager.GetString("DefaultBehaviorForRiderLinkUpdate_Text");
    public static string BuildAndInstall_RadioButton_Text => ResourceManager.GetString("BuildAndInstall_RadioButton_Text");
    public static string ExtractOnly_RadioButton_Text => ResourceManager.GetString("ExtractOnly_RadioButton_Text");
    public static string InstallOrExtractRadio_Comment_Text => ResourceManager.GetString("InstallOrExtractRadio_Comment_Text");
    public static string AutoupdateRiderLink_CheckBox_Text => ResourceManager.GetString("AutoupdateRiderLink_CheckBox_Text");
    public static string Settings_AutoUpdate_Header_Text => ResourceManager.GetString("Settings_AutoUpdate_Header_Text");
    public static string Settings_General_Header_Text => ResourceManager.GetString("Settings_General_Header_Text");
    public static string Settings_ManualInstallation_Header_Text => ResourceManager.GetString("Settings_ManualInstallation_Header_Text");
    public static string DefaultLocationForRiderLinkInstallation_Text => ResourceManager.GetString("DefaultLocationForRiderLinkInstallation_Text");
    public static string InstallInEngine_Text => ResourceManager.GetString("InstallInEngine_Text");
    public static string InstallInGame_Text => ResourceManager.GetString("InstallInGame_Text");
  }
}