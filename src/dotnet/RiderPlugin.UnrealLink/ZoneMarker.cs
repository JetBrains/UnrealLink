﻿using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Host.Env;
using JetBrains.ReSharper.Host.Product;
using JetBrains.ReSharper.Psi;

namespace RiderPlugin.UnrealLink
{
    [ZoneMarker]
    public class ZoneMarker :
        IRequire<ILanguageCppZone>,
        IRequire<DaemonZone>,
        IRequire<IRiderFeatureZone>,
        IRequire<IRiderProductEnvironmentZone>
    {
    }
}