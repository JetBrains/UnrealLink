#include "LifetimeDefinition.h"

#include <spdlog/spdlog.h>

namespace rd
{
LifetimeDefinition::LifetimeDefinition(bool eternaled, const std::string& name) : eternaled(eternaled), lifetimeName(name), lifetime(eternaled, name)
{
	spdlog::log(spdlog::level::info, "lfd created: {}", lifetimeName);
}

LifetimeDefinition::LifetimeDefinition(const Lifetime& parent, const std::string& name) :
	LifetimeDefinition(false, name + " :> " + parent.ptr->GetName())
{
	parent->attach_nested(lifetime.ptr);
}

bool LifetimeDefinition::is_terminated() const
{
	return lifetime->is_terminated();
}

void LifetimeDefinition::terminate()
{
	spdlog::log(spdlog::level::info, "lfd terminated: {}", lifetimeName);
	lifetime->terminate();
}

bool LifetimeDefinition::is_eternal() const
{
	return lifetime->is_eternal();
}

namespace
{
LifetimeDefinition ETERNAL(true, "Eternal Definition");
}

std::shared_ptr<LifetimeDefinition> LifetimeDefinition::get_shared_eternal()
{
	return std::shared_ptr<LifetimeDefinition>(&ETERNAL, [](LifetimeDefinition* /*ld*/) {});
}

LifetimeDefinition::~LifetimeDefinition()
{	
	spdlog::log(spdlog::level::info, "lfd destroyed: {}", lifetimeName);
	if (lifetime.ptr != nullptr)
	{	 // wasn't moved
		if (!is_eternal())
		{
			if (!lifetime->is_terminated())
			{
				lifetime->terminate();
			}
		}
	}
}
}	 // namespace rd
