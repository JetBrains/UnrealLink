using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Psi;

namespace RiderPlugin.UnrealLink
{
    [ZoneMarker]
    public class ZoneMarker :
        IRequire<ILanguageCppZone>,
        IRequire<DaemonZone>
    {
    }
}