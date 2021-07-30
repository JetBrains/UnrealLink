#ifndef RD_CPP_FRAMEWORK_RNAME_H
#define RD_CPP_FRAMEWORK_RNAME_H

#if defined(_MSC_VER)
#pragma warning(push)
#pragma warning(disable:4251)
#endif

#include "thirdparty.hpp"

#include <string>
#include <rd_framework_export.h>

namespace rd
{
class RNameImpl;

/**
 * \brief Recursive name. For constructs like Aaaa.Bbb::CCC
 */
class RD_FRAMEWORK_API RName
{
public:
	// region ctor/dtor

	RName() = default;

	RName(const RName& Other) = default;

	RName(RName&& Other) noexcept = default;

	RName& operator=(const RName& Other) = default;

	RName& operator=(RName&& Other) noexcept = default;

	RName(const RName& Parent, const std::string& LocalName, const std::string& Separator);

	explicit RName(const std::string&  LocalName);
	// endregion

	RName sub( const std::string& LocalName, const std::string& Separator) const;

	explicit operator bool() const
	{
		return !name.empty();
	}

	friend std::string RD_FRAMEWORK_API to_string(RName const& Value);
private:	
	std::string name;
	
};
}	 // namespace rd
#if defined(_MSC_VER)
#pragma warning(pop)
#endif


#endif	  // RD_CPP_FRAMEWORK_RNAME_H
