using System;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using JetBrains.Application;
using JetBrains.Application.Environment;
using JetBrains.Extension;
using JetBrains.Util;
using Newtonsoft.Json.Linq;

namespace RiderPlugin.UnrealLink.PluginInstaller
{
    [ShellComponent]
    public class PluginPathsProvider
    {
        private readonly ApplicationPackages myApplicationPackages;
        private readonly IDeployedPackagesExpandLocationResolver myResolver;
        private readonly ILogger myLogger;

        private static readonly string EditorPluginFile = "RiderLink.zip";
        public readonly FileSystemPath PathToPackedPlugin;
        public readonly byte[] CurrentPluginChecksum;
        private readonly byte[] NullChecksum = { 0 };

        public PluginPathsProvider(ApplicationPackages applicationPackages,
            IDeployedPackagesExpandLocationResolver resolver, ILogger logger)
        {
            myApplicationPackages = applicationPackages;
            myResolver = resolver;
            myLogger = logger;
            PathToPackedPlugin = GetEditorPluginPathFile();
            CurrentPluginChecksum = GetCurrentPluginChecksum();
        }

        private FileSystemPath GetEditorPluginPathFile()
        {
            var assembly = Assembly.GetExecutingAssembly();
            var package = myApplicationPackages.FindPackageWithAssembly(assembly, OnError.LogException);
            var installDirectory = myResolver.GetDeployedPackageDirectory(package);
            var editorPluginPathFile = installDirectory.Parent.Combine($@"EditorPlugin/{EditorPluginFile}");
            return editorPluginPathFile;
        }

        private byte[] GetCurrentPluginChecksum()
        {
            var editorPluginPathFile = PathToPackedPlugin;
            using var zipArchive = ZipFile.OpenRead(editorPluginPathFile.FullPath);
            var zipArchiveEntry = zipArchive.GetEntry(UnrealPluginDetector.CHEKCSUM_ENTRY_PATH);
            var stream = zipArchiveEntry?.Open();
            return stream?.ReadAllBytes();
        }

        public byte[] GetPluginChecksum(VirtualFileSystemPath checksumPath)
        {
            if (!checksumPath.ExistsFile)
            {
                return NullChecksum;
            }
            try
            {
                return File.ReadAllBytes(checksumPath.FullPath);
            }
            catch (Exception exception)
            {
                myLogger.Error(exception, "[UnrealLink]: Couldn't read RiderLink plugin version");
                return null;
            }
        }
    }
}