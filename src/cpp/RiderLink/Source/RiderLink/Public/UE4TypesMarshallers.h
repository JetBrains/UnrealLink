#pragma once

#include "std/hash.h"
#include "Polymorphic.h"

#include "Containers/UnrealString.h"


//region FString

namespace rd {
	template <>
	class Polymorphic<FString> {
	public:
		static FString read(SerializationCtx& ctx, Buffer& buffer);

		static void write(SerializationCtx& ctx, Buffer& buffer, FString const& value);
	};

	template <>
	class Polymorphic<Wrapper<FString>>
	{
	public:
		static void write(SerializationCtx& ctx, Buffer& buffer, Wrapper<FString> const& value);
	};

	template <>
	std::string to_string(FString const& val);

	template <>
	struct hash<FString> {
		size_t operator()(const FString& value) const noexcept;
	};
}

extern template class rd::Polymorphic<FString>;
extern template class rd::Polymorphic<rd::Wrapper<FString>>;
extern template struct rd::hash<FString>;

//endregion
