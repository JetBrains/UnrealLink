#pragma once

#include <protocol/Protocol.h>

#include "Templates/UniquePtr.h"

class ProtocolFactory {
public:
    static TUniquePtr<rd::Protocol> Create(rd::IScheduler * Scheduler, rd::Lifetime SocketLifetime);
};
