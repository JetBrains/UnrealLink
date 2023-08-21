using JetBrains.Build;
using RiderPlugin.UnrealLink.BuildScript;
using JetBrains.Rider.Backend.Install;

namespace RiderPlugin.UnrealLink.Install
{
  public static class AdvertiseRiderBundledPlugin
  {
    [BuildStep]
    public static RiderBundledProductArtifact[] ShipUnrealLinkWithRider()
    {
      return new[]
      {
        new RiderBundledProductArtifact(
          UnrealLinkInRiderProduct.ProductTechnicalName,
          UnrealLinkInRiderProduct.ThisSubplatformName,
          UnrealLinkInRiderProduct.DotFilesFolder,
          allowCommonPluginFiles: false),
        new RiderBundledProductArtifact(
          UnrealLinkInRiderProduct.EditorPlugin.ProductTechnicalName,
          UnrealLinkInRiderProduct.EditorPlugin.EditorSubplatformName,
          UnrealLinkInRiderProduct.EditorPlugin.EditorFolder,
          allowCommonPluginFiles: false)
      };
    }
  }
}