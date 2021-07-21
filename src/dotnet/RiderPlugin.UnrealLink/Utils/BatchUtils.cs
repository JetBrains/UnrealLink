using System;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.Utils
{
    public static class BatchUtils
    {
        public static VirtualFileSystemPath GetPathToCmd()
        {
            var comspec = InteractionContext.SolutionContext.EnvironmentInteraction.GetEnvironmentVariable("COMSPEC");
            var pathToCmd = VirtualFileSystemPath.Parse(comspec, InteractionContext.SolutionContext);
            return pathToCmd;
        }
    }
}