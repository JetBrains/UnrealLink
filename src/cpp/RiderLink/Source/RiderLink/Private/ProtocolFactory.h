#pragma once

#include "rd_framework_cpp/protocol/Protocol.h"

#include "Templates/UniquePtr.h"

class ProtocolFactory {
public:
    static TUniquePtr<rd::Protocol> Create(rd::IScheduler * Scheduler, rd::Lifetime SocketLifetime);
};
