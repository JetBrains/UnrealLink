#pragma once

#include "protocol/Protocol.h"
#include "scheduler/base/IScheduler.h"

#include "Templates/UniquePtr.h"

class ProtocolFactory {
public:
    static TUniquePtr<rd::Protocol> create(rd::IScheduler & scheduler, rd::Lifetime socketLifetime);
};
