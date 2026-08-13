using System;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.Utils
{
    public class DeleteTempFolders : IDisposable
    {
        private readonly VirtualFileSystemPath myTempFolder;

        public DeleteTempFolders(VirtualFileSystemPath tempFolder)
        {
            myTempFolder = tempFolder;
        }

        public void Dispose()
        {
            myTempFolder.Delete();
        }
    }
    
    public class BackupDir
    {
        private readonly VirtualFileSystemPath myOldDir;
        private readonly VirtualFileSystemPath myBackupDir;

        // backupRoot must be the root the build tree uses: DeleteTempFolders wipes the shared prefix
        // folder under it, and that is what cleans the backups up.
        public BackupDir(VirtualFileSystemPath oldDir, VirtualFileSystemPath backupRoot, string backupFolderPrefix)
        {
            myOldDir = oldDir;
            myBackupDir = VirtualFileSystemDefinition.CreateTemporaryDirectory(InteractionContext.SolutionContext, backupRoot, backupFolderPrefix);
            myOldDir.CopyDirectory(myBackupDir);
            myOldDir.Delete();
        }

        public void Restore()
        {
            myOldDir.Delete();
            myBackupDir.CopyDirectory(myOldDir);
        }
    }
}