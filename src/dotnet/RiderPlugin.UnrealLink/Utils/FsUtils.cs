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

        public BackupDir(VirtualFileSystemPath oldDir, string backupFolderPrefix)
        {
            myOldDir = oldDir;
            myBackupDir = VirtualFileSystemDefinition.CreateTemporaryDirectory(InteractionContext.SolutionContext, null, backupFolderPrefix);
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