﻿using System.Reflection;
using JetBrains.Annotations;
using JetBrains.Lifetimes;
using JetBrains.ReSharper.Feature.Services.Cpp.ProjectModel.UE4;
using JetBrains.Util;

namespace RiderPlugin.UnrealLink.Utils
{
    public static class CmdUtils
    {
        public static uint RunCommandWithLock(Lifetime lifetime, [NotNull] InvokeChildProcess.StartInfo startinfo, [CanBeNull] ILogger logger)
        {
            lock (HACK_getMutexForUBT())
            {
                return InvokeChildProcess.InvokeCore(lifetime, startinfo,
                    InvokeChildProcess.SyncAsync.Sync, logger).Result;
            }
        }
        
        public static string GetPlatformCmdExtension()
        {
            switch (PlatformUtil.RuntimePlatform)
            {
                case PlatformUtil.Platform.Windows:
                    return "bat";
                case PlatformUtil.Platform.MacOsX:
                    return "command";
                default:
                    return "sh";
            }
        }
        
        public static InvokeChildProcess.StartInfo GetProcessStartInfo([NotNull] InvokeChildProcess.PipeStreams pipeStreams,
            [NotNull] VirtualFileSystemPath cmd, [CanBeNull] VirtualFileSystemPath workindDir, params string[] args)
        {
            var command = GetPlatformCommand(cmd);
            var commandLine = GetPlatformCommandLine(cmd, args);

            var startInfo = new InvokeChildProcess.StartInfo(command.ToNativeFileSystemPath())
            {
                Arguments = commandLine,
                Pipe = pipeStreams,
                CurrentDirectory = workindDir?.ToNativeFileSystemPath()
            };
            
            return startInfo;
        }

        private static VirtualFileSystemPath GetPlatformCommand([NotNull] VirtualFileSystemPath command)
        {
            return PlatformUtil.RuntimePlatform == PlatformUtil.Platform.Windows ? GetPathToCmd() : command;
        }
        
        private static CommandLineBuilderJet GetPlatformCommandLine([NotNull] VirtualFileSystemPath command, params string[] args)
        {
            var commandLine = new CommandLineBuilderJet();
            if (PlatformUtil.RuntimePlatform == PlatformUtil.Platform.Windows)
            {
                commandLine.AppendFileName(command.ToNativeFileSystemPath());
            }

            foreach (var arg in args)
            {
                commandLine.AppendSwitch(arg);
            }

            if (PlatformUtil.RuntimePlatform == PlatformUtil.Platform.Windows)
            {
                return new CommandLineBuilderJet().AppendSwitch("/C")
                    .AppendSwitch($"\"{commandLine}\"");
            }

            return commandLine;
        }

        private static VirtualFileSystemPath GetPathToCmd()
        {
            var comspec = InteractionContext.SolutionContext.EnvironmentInteraction.GetEnvironmentVariable("COMSPEC");
            var pathToCmd = VirtualFileSystemPath.Parse(comspec, InteractionContext.SolutionContext);
            return pathToCmd;
        }
        
        private static object HACK_getMutexForUBT()
        {
            var field =
                typeof(CppUE4UbtRunner).GetField("ourLocker", BindingFlags.Static | BindingFlags.NonPublic);
            return field.GetValue(null);
        }
    }
}