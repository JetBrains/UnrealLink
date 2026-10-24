using System;
using System.Reflection;
using JetBrains.Annotations;
using JetBrains.HabitatDetector;
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
        
        /// <summary>
        /// Runs the command under the UBT lock. The timeout starts after the lock is taken.
        /// When the process does not exit in time, this method kills it and returns <c>false</c>.
        /// </summary>
        public static bool TryRunCommandWithLock(Lifetime lifetime, [NotNull] InvokeChildProcess.StartInfo startinfo,
            [CanBeNull] ILogger logger, TimeSpan timeout, out uint exitCode)
        {
            lock (HACK_getMutexForUBT())
            {
                var processLifetime = lifetime.CreateTerminatedAfter(timeout);
                exitCode = InvokeChildProcess.InvokeCore(processLifetime, startinfo,
                    InvokeChildProcess.SyncAsync.Sync, logger).Result;

                // Only the timeout terminates the process lifetime while the outer lifetime is still alive.
                var timedOut = processLifetime.IsNotAlive && lifetime.IsAlive;
                if (timedOut)
                    logger?.Warn($"[UnrealLink]: The process did not exit in {timeout}, it was killed");

                return !timedOut;
            }
        }

        public static string GetPlatformCmdExtension()
        {
            switch (PlatformUtil.RuntimePlatform)
            {
                case JetPlatform.Windows:
                    return "bat";
                case JetPlatform.MacOsX:
                    return "command";
                default:
                    return "sh";
            }
        }
        
        public static InvokeChildProcess.StartInfo GetProcessStartInfo([NotNull] InvokeChildProcess.PipeStreams pipeStreams,
            [NotNull] VirtualFileSystemPath cmd, [CanBeNull] VirtualFileSystemPath workingDir, params string[] args)
        {
            var command = GetPlatformCommand(cmd);
            var commandLine = GetPlatformCommandLine(cmd, args);

            var startInfo = new InvokeChildProcess.StartInfo(command.ToNativeFileSystemPath())
            {
                Arguments = commandLine,
                Pipe = pipeStreams,
                CurrentDirectory = workingDir?.ToNativeFileSystemPath()
            };
            
            return startInfo;
        }

        private static VirtualFileSystemPath GetPlatformCommand([NotNull] VirtualFileSystemPath command)
        {
            return PlatformUtil.RuntimePlatform == JetPlatform.Windows ? GetPathToCmd() : command;
        }
        
        private static CommandLineBuilderJet GetPlatformCommandLine([NotNull] VirtualFileSystemPath command, params string[] args)
        {
            var commandLine = new CommandLineBuilderJet();
            if (PlatformUtil.RuntimePlatform == JetPlatform.Windows)
            {
                commandLine.AppendFileName(command.ToNativeFileSystemPath());
            }

            foreach (var arg in args)
            {
                commandLine.AppendSwitch(arg);
            }

            if (PlatformUtil.RuntimePlatform == JetPlatform.Windows)
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