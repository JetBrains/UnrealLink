using System;
using System.IO;
using System.IO.Compression;

namespace Zipper
{
    internal class Program
    {
        public static void Main(string[] args)
        {
            if (args.Length != 2)
            {
                System.Console.WriteLine("Expecting 2 arguments, source directory and destination directory");
                return;
            }

            string Source = args[0];
            string Destination = args[1];
            if (!Destination.EndsWith("zip", StringComparison.InvariantCultureIgnoreCase))
            {
                System.Console.WriteLine("Destination should be an absolute path to .zip file");
                return;
            }

            try
            {
                if(File.Exists(Destination)) File.Delete(Destination);
                using var zip = ZipFile.Open(Destination, ZipArchiveMode.Create);
                var riderlinkUpluginFilename = "RiderLink.uplugin";
                var upluginFileFullPath = Path.Combine(Source, riderlinkUpluginFilename);
                if (!File.Exists(upluginFileFullPath))
                {
                    System.Console.WriteLine($"[Error]: Missing {upluginFileFullPath}");
                    return;
                }

                zip.CreateEntryFromFile(upluginFileFullPath, riderlinkUpluginFilename);
                var subfolders = new[] {"Source", "Resources"};
                foreach (var subfolder in subfolders)
                {
                    var subfolderFullPath = Path.Combine(Source, subfolder);
                    if (!Directory.Exists(subfolderFullPath))
                    {
                        System.Console.WriteLine($"[Error]: Missing {subfolderFullPath}");
                        return;
                    }
                    foreach (var filePath in Directory.EnumerateFiles(subfolderFullPath, "*", SearchOption.AllDirectories))
                    {
                        var entryName = filePath.Replace(Source + Path.DirectorySeparatorChar, "");
                        entryName = entryName.Replace('\\', '/');
                        zip.CreateEntryFromFile(filePath, entryName);
                    }
                }
            }
            catch (Exception e)
            {
                System.Console.WriteLine($"Couldn't pack {Source} into {Destination}");
                System.Console.Write(e);
            }
        }
    }
}