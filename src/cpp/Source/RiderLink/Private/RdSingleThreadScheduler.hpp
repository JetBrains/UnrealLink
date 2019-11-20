#pragma once

#include "IScheduler.h"

#include "Async/AsyncWork.h"

#include <atomic>
#include <cassert>
#include <functional>

class RdSingleThreadScheduler : public rd::IScheduler {
public:
  //  RdSingleThreadScheduler(const std::wstring& name) {
		//// pool = FQueuedThreadPool::Allocate();
		//// pool->Create(2, (32 * 1024), TPri_Normal);
  //  }

    
    void flush() override {
        // override fun flush() {
        //     require(!isActive) {"Can't flush this scheduler in a reentrant way: we are inside queued item's execution"}

        //     SpinWait.spinUntil { tasksInQueue.get() == 0 }
        // }
        assert(!is_active() && "Can't flush this scheduler in a reentrant way: we are inside queued item's execution");

        while(tasksInQueue.load() != 0){}
    };

    class RdTask : public FNonAbandonableTask {
    public:
        RdTask(std::function<void()> action): _action(action){}
        void DoWork() {
            _action();
        }
		FORCEINLINE TStatId GetStatId() const
		{
			RETURN_QUICK_DECLARE_CYCLE_STAT(RdTask, STATGROUP_ThreadPoolAsyncTasks);
		}
    private:
        std::function<void()> _action;
	};

    void queue(std::function<void()> action) override {
        ++tasksInQueue;
        auto task = [this, action](){
            ++active;
            action();
            --active;
            --tasksInQueue;
        };
        auto  asyncTask = new FAsyncTask<RdTask>(task);
        asyncTask->StartBackgroundTask();
        taskQueue.Add(asyncTask);
    };

    bool is_active() const override {
        // override val isActive: Boolean get() = active > 0
        return active > 0;
    };

    //void assert_thread() const override;

private:
    std::atomic_uint32_t tasksInQueue{0};
	std::atomic_uint32_t active{0};
    // FQueuedThreadPool * pool;
    TArray<FAsyncTask<RdTask>* > taskQueue;
};