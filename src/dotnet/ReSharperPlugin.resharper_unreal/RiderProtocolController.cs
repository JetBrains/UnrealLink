using System;
using JetBrains.Collections.Viewable;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.Rd.Impl;

namespace ReSharperPlugin.UnrealEditor
{
  // ReSharper disable once UnusedMember.Global
  public class RiderProtocolController
  {
    public readonly SocketWire.Server Wire;
    private static readonly ILog ourLogger = Log.GetLog<RiderProtocolController>();

    public RiderProtocolController(IScheduler mainThreadScheduler, Lifetime lifetime)
    {
      try
      {
        ourLogger.Verbose("Start ControllerTask...");

        Wire = new SocketWire.Server(lifetime, mainThreadScheduler, null, "UnityServer");
        ourLogger.Verbose("Created SocketWire with port = {0}", Wire.Port);
      }
      catch (Exception ex)
      {
        ourLogger.Error(ex, "RiderProtocolController.ctor. ");
      }
    }
  }
  
//  [Serializable]
}