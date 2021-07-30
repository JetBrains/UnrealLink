#include "RName.h"

#include "thirdparty.hpp"

namespace rd
{

RName::RName(const RName& Parent, const std::string& LocalName, const std::string& Separator) :
	name(to_string(Parent) + Separator + LocalName)
{
}

RName RName::sub(const std::string&  LocalName, const std::string&  Separator) const
{
	return RName(*this, LocalName, Separator);
}

std::string to_string(RName const& Value)
{
	return Value.name;
}

RName::RName(const std::string&  LocalName) : name(LocalName)
{
}

}	 // namespace rd
