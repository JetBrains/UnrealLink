#pragma once

#include "serialization/Polymorphic.h"
#include "std/hash.h"

#include "Containers/UnrealString.h"
#include "Containers/StringConv.h"
#include "Templates/UniquePtr.h"


//region FString

inline std::string to_string(FString const& val) {
    return TCHAR_TO_UTF8(*val);
}

template <typename T, typename A>
int32_t size(TArray<T, A> const& value) {
    return static_cast<int32_t>(value.Num());
}

template <typename T, typename A>
void resize(TArray<T, A>& value, int32_t size) {
    value.SetNum(size);
}

namespace rd {
    template <>
    class Polymorphic<FString> {
    public:
        static FString read(SerializationCtx& ctx, Buffer& buffer);

        static void write(SerializationCtx& ctx, Buffer& buffer, FString const& value);
    };

    template <>
    class Polymorphic<Wrapper<FString>> {
    public:
        static void write(SerializationCtx& ctx, Buffer& buffer, Wrapper<FString> const& value);
    };

    template <>
    struct hash<FString> {
        size_t operator()(const FString& value) const noexcept;
    };

    // template <typename T>
    // std::string to_string(TArray<T> const& val);

    template <typename T>
    struct hash<TArray<T>> {
        size_t operator()(const TArray<T>& value) const noexcept;
    };

    // rd's generic contentDeepHashCode SFINAE in util/gen_util.h relies on ADL `begin(T{})`,
    // which TArray (global namespace) doesn't provide as a free function. Supply a non-template
    // overload for TArray<FString> so generated hashCode bodies that hash list-of-FString fields
    // (e.g. UE4Library::BatchScriptRequest) compile.
    inline size_t contentDeepHashCode(TArray<FString> const& value) noexcept {
        size_t result = 1;
        for (auto const& x : value) {
            result = 31 * result + hash<FString>()(x);
        }
        return result;
    }

    template <typename T>
    Wrapper<T> ToRdWrapper(TUniquePtr<T>&& Ptr) {
        Wrapper<T> Result;
        Result.reset(std::move(Ptr).Release());
        return Result;
    }
}

extern template class rd::Polymorphic<FString>;
extern template class rd::Polymorphic<rd::Wrapper<FString>>;
extern template struct rd::hash<FString>;
// extern template class rd::Polymorphic<TArray<FString>, void>;

//endregion
