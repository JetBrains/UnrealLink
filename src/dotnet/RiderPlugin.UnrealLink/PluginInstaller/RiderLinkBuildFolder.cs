using JetBrains.HabitatDetector;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.PluginInstaller
{
    /// <summary>
    /// Picks the root for every temporary folder RiderLink creates: the build tree and the backups of
    /// previously installed plugins.
    /// </summary>
    /// <remarks>
    /// On macOS the root must not be the OS temp directory. Starting with UE 5.8, Unreal Build
    /// Accelerator keeps temp permanently empty in its directory table
    /// (<c>SessionCreateInfo.treatTempDirAsEmpty</c>), so build outputs written under it are never
    /// registered and never flushed to disk, and linking fails with missing object files.
    /// Only macOS is confirmed to be affected, and moving the root off temp costs about 37 characters
    /// of the Windows MAX_PATH budget, so every other platform keeps using the OS temp directory.
    /// See RIDER-141850.
    /// </remarks>
    public static class RiderLinkBuildFolder
    {
        private static bool AvoidOsTemp => PlatformUtil.RuntimePlatform == JetPlatform.MacOsX;

        public static VirtualFileSystemPath GetDefaultRoot(IInteractionContext context)
        {
            if (!AvoidOsTemp) return VirtualFileSystemDefinition.GetTempPath(context);

            // Rider points JET_TEMP_DIR at its own system directory, so the company temp folder
            // normally already lives outside the OS temp directory.
            var companyTemp = CompanySpecificFolderLocations.TempFolder;
            if (IsUnderOsTemp(companyTemp))
            {
                // JET_TEMP_DIR was not redirected. Local app data never lives under temp.
                companyTemp = CompanySpecificFolderLocations.LocalAppdata / "RiderLink";
            }

            return companyTemp.ToVirtualFileSystemPath();
        }

        /// <summary>
        /// Whether RiderLink cannot be built in <paramref name="path"/> on the current platform.
        /// </summary>
        public static bool IsUnsupported(FileSystemPath path) => AvoidOsTemp && IsUnderOsTemp(path);

        private static bool IsUnderOsTemp(FileSystemPath path)
        {
            if (path.IsEmpty) return false;
            var osTemp = FileSystemDefinition.GetTempPath();
            return !osTemp.IsEmpty && Canonicalize(osTemp).IsPrefixOf(Canonicalize(path));
        }

        // On macOS /var and /tmp are symlinks into /private, so the same directory appears under two
        // names depending on who resolved it. Compare the form without the /private prefix.
        private static FileSystemPath Canonicalize(FileSystemPath path)
        {
            if (PlatformUtil.RuntimePlatform != JetPlatform.MacOsX) return path;

            var fullPath = path.FullPath;
            return fullPath.StartsWith("/private/")
                ? FileSystemPath.Parse(fullPath.Substring("/private".Length))
                : path;
        }
    }
}
