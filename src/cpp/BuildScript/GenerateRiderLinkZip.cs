using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Security.Cryptography;
using JetBrains.Application.BuildScript;
using JetBrains.Application.BuildScript.Compile;
using JetBrains.Application.BuildScript.Solution;
using JetBrains.Build;
using JetBrains.Extension;
using JetBrains.Util;
using JetBrains.Util.dataStructures;
using JetBrains.Util.Special;
using JetBrains.Util.Storage;

namespace Plugins.UnrealLink.src.cpp.BuildScript
{
  public class GenerateRiderLinkZip
  {
    [BuildStep]
    public static SubplatformFileForPackaging[] Run(AllAssembliesOnEverything allass, ProductHomeDirArtifact homedir, ILogger logger)
    {
      if (allass.FindSubplatformByClass<GenerateRiderLinkZip>() is SubplatformOnSources subplatform)
      {
        FileSystemPath dirRiderLink = homedir.ProductHomeDir / subplatform.Name.RelativePath / "RiderLink";

        IList<ImmutableFileItem> files = dirRiderLink.GetChildFiles(flags: PathSearchFlags.RecurseIntoSubdirectories)
          .OrderBy().Select(file => ImmutableFileItem.CreateFromDisk(file).WithRelativePath(file.MakeRelativeTo(dirRiderLink))).ToList();

        ImmutableFileItem fiUpluginTemplate = files.Single(fi => fi.RelativePath == "RiderLink.uplugin.template");
        StreamEx.TextAndEncoding taeUpluginTemplate = fiUpluginTemplate.FileContent.ReadTextFromFile();
        string sUpluginContent = taeUpluginTemplate.Text.Replace("%PLUGIN_VERSION%", GlobalDefines.FullMarketingVersionString);
        ImmutableFileItem fiUplugin = new ImmutableFileItem(fiUpluginTemplate.RelativePath.NameWithoutExtension, ImmutableByteStream.FromByteArray(taeUpluginTemplate.Encoding.GetBytes(sUpluginContent).ToImmutableArray()));

        byte[] bytesChecksumContent = MD5.Create().WithDispose(hasher =>
        {
          foreach (var fi in files) hasher.TransformBlock(fi.FileContent);
          hasher.TransformFinalBlock(Array.Empty<byte>(), 0, 0);
          return hasher.Hash;
        });
        ImmutableFileItem fiChecksum = new ImmutableFileItem("Resources/checksum", ImmutableByteStream.FromByteArray(bytesChecksumContent.ToImmutableArray()));

        ImmutableFileItem fiZip = Compression.ZipCompress(dirRiderLink.Name + ExtensionConstants.Zip, files.Where(fi => fi != fiUpluginTemplate).Concat(fiUplugin, fiChecksum), logger, whencompress: Compression.WhenToCompressEntry.Always);
        return new[] { new SubplatformFileForPackaging(subplatform.Name, fiZip) };
      }

      return Array.Empty<SubplatformFileForPackaging>();
    }
  }
}