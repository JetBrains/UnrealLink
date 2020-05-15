using System;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.Utils
{
    public static class BatchUtils
    {
        public static FileSystemPath GetPathToCmd()
        {
            var comspec = Environment.GetEnvironmentVariable("COMSPEC");
            var pathToCmd = FileSystemPath.Parse(comspec);
            return pathToCmd;
        }
    }
}