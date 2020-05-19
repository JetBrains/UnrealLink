using System;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.Utils
{
    public class DeleteTempFolders : IDisposable
    {
        private readonly FileSystemPath myTempFolder;

        public DeleteTempFolders(FileSystemPath tempFolder)
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
        private readonly FileSystemPath myOldDir;
        private readonly FileSystemPath myBackupDir;

        public BackupDir(FileSystemPath oldDir, string backupFolderPrefix)
        {
            myOldDir = oldDir;
            myBackupDir = FileSystemDefinition.CreateTemporaryDirectory(null, backupFolderPrefix);
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