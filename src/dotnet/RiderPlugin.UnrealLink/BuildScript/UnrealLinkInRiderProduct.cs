using System;
using System.Linq;
using JetBrains.Application.BuildScript.Compile;
using JetBrains.Application.BuildScript.PackageSpecification;
using JetBrains.Application.BuildScript.Solution;
using JetBrains.Build;
using JetBrains.Rider.Backend.BuildScript;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.BuildScript
{
  /// <summary>
  ///   Defines a bundled plugin which drives adding the referenced packages as a plugin for Rider.
  /// </summary>
  public class UnrealLinkInRiderProduct
  {
    public static readonly SubplatformName ThisSubplatformName = new((RelativePath)"Plugins" / "UnrealLink" / "src" / "dotnet");

    public static readonly RelativePath DotFilesFolder = @"plugins\UnrealLink\dotnet";

    public const string ProductTechnicalName = "UnrealLink";

    [BuildStep]
    public static SubplatformComponentForPackagingFast[] ProductMetaDependency(AllAssembliesOnSources allassSrc)
    {
      if (allassSrc.Subplatforms.All(sub => sub.Name != ThisSubplatformName))
        return Array.Empty<SubplatformComponentForPackagingFast>();

      return new[]
      {
        new SubplatformComponentForPackagingFast
        (
          ThisSubplatformName,
          new JetPackageMetadata
          {
            Spec = new JetSubplatformSpec
            {
              ComplementedProductName = RiderConstants.ProductTechnicalName
            }
          }
        )
      };
    }

    public class EditorPlugin
    {
      public static readonly SubplatformName EditorSubplatformName = new((RelativePath)"Plugins" / "UnrealLink" / "src" / "cpp");

      public static readonly RelativePath EditorFolder = @"plugins\UnrealLink\EditorPlugin";

      public const string ProductTechnicalName = "UnrealLink.EditorPlugin";

      [BuildStep]
      public static SubplatformComponentForPackagingFast[] ProductMetaDependency(AllAssembliesOnSources allassSrc)
      {
        if (allassSrc.Subplatforms.All(sub => sub.Name != EditorSubplatformName))
          return Array.Empty<SubplatformComponentForPackagingFast>();

        return new[]
        {
          new SubplatformComponentForPackagingFast
          (
            EditorSubplatformName,
            new JetPackageMetadata
            {
              Spec = new JetSubplatformSpec
              {
                ComplementedProductName = RiderConstants.ProductTechnicalName
              }
            }
          )
        };
      }
    }
  }
}