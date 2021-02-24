using System;
using System.Collections.Generic;
using JetBrains.Annotations;
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

    public static class FsUtils
    {      
        public static void ExhaustActions([NotNull] this Stack<Action> actions)
        {
            while (!actions.IsEmpty())
            {
                actions.Pop().Invoke();
            }
        }

        public static Action BackupDir(FileSystemPath oldDir, string backupFolderPrefix)
        {
            var myOldDir = oldDir;
            var myBackupDir = FileSystemDefinition.CreateTemporaryDirectory(null, backupFolderPrefix);
            myOldDir.CopyDirectory(myBackupDir);
            myOldDir.Delete();
            return () =>
            {
                myOldDir.Delete();
                myBackupDir.CopyDirectory(myOldDir);
            };

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