using System;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using JetBrains.Application;
using JetBrains.Application.Environment;
using JetBrains.Util;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace RiderPlugin.UnrealLink.PluginInstaller
{
    [ShellComponent]
    public class PluginPathsProvider
    {
        private readonly ApplicationPackages myApplicationPackages;
        private readonly IDeployedPackagesExpandLocationResolver myResolver;

        private static readonly string EditorPluginFile = "RiderLink.zip";
        public readonly FileSystemPath PathToPackedPlugin;
        public readonly Version CurrentPluginVersion;

        public PluginPathsProvider(ApplicationPackages applicationPackages,
            IDeployedPackagesExpandLocationResolver resolver)
        {
            myApplicationPackages = applicationPackages;
            myResolver = resolver;
            PathToPackedPlugin = GetEditorPluginPathFile();
            CurrentPluginVersion = GetCurrentPluginVersion();
        }

        private FileSystemPath GetEditorPluginPathFile()
        {
            var assembly = Assembly.GetExecutingAssembly();
            var package = myApplicationPackages.FindPackageWithAssembly(assembly, OnError.LogException);
            var installDirectory = myResolver.GetDeployedPackageDirectory(package);
            var editorPluginPathFile = installDirectory.Parent.Combine($@"EditorPlugin/{EditorPluginFile}");
            return editorPluginPathFile;
        }

        private Version GetCurrentPluginVersion()
        {
            Version result = null;
            var editorPluginPathFile = PathToPackedPlugin;
            using var zipArchive = ZipFile.OpenRead(editorPluginPathFile.FullPath);
            var zipArchiveEntry = zipArchive.GetEntry(UnrealPluginDetector.UPLUGIN_FILENAME);
            if (zipArchiveEntry == null) return null;

            var stream = zipArchiveEntry.Open();
            using var streamReader = new StreamReader(stream);
            using var jsonReader = new JsonTextReader(streamReader);
            while (jsonReader.Read())
            {
                if (jsonReader.Value == null || jsonReader.TokenType != JsonToken.PropertyName) continue;

                if (jsonReader.Value.ToString()
                    .Equals("VersionName", StringComparison.InvariantCultureIgnoreCase))
                {
                    var versionAsString = jsonReader.ReadAsString();
                    result = Version.Parse(versionAsString);
                    break;
                }
            }

            return result;
        }

        public static Version GetPluginVersion(FileSystemPath upluginFilePath)
        {
            var text = File.ReadAllText(upluginFilePath.FullPath);
            try

            {
                var upluginDescription = JObject.Parse(text);
                var versionToken = upluginDescription.GetValue("VersionName");
                if (versionToken == null) return null;

                return new Version(versionToken.ToString());
            }
            catch (Exception _)
            {
                // TODO: add logging for multiple cases
                return null;
            }
        }
    }
}