#pragma once

#include "UE4TypesMarshallers.h"

#include "Containers/StringConv.h"
#include "ArraySerializer.h"

//region FString

namespace rd {
	FString Polymorphic<FString, void>::read(SerializationCtx& ctx, Buffer& buffer) {
		return FString(std::move(buffer.read_wstring()).data());
	}

	void Polymorphic<FString, void>::write(SerializationCtx& ctx, Buffer& buffer, FString const& value) {
		buffer.write_wstring(wstring_view(GetData(value), value.Len()));
	}

	template <>
	std::string rd::to_string<FString>(FString const& val) {
		return TCHAR_TO_UTF8(*val);
	}


	size_t hash<FString>::operator()(const FString& value) const noexcept {
		return GetTypeHash(value);
	}

    void Polymorphic<Wrapper<FString>>::write(SerializationCtx& ctx, Buffer& buffer, Wrapper<FString> const& value) {
        buffer.write_wstring(wstring_view(GetData(*value), value->Len()));
    }

    template <typename T>
    TArray<T> Polymorphic<TArray<T>, void>::read(SerializationCtx& ctx, Buffer& buffer) {
        const int32_t Len = buffer.read_integral<int32_t>();
        TArray<T> result;
        result.Empty(Len);
        for (auto& it : result) {
            it = Polymorphic<T>::read(ctx, buffer);
        }
        return result;
    }

    template <typename T>
    void Polymorphic<TArray<T>, void>::write(SerializationCtx& ctx, Buffer& buffer, TArray<T> const& value) {
        const int32_t len = value.Num();
        buffer.write_integral<int32_t>(len);
        for (auto& it : value) {
            Polymorphic<T>::write(ctx, buffer, it);
        }
    }

    template <typename T>
    std::string to_string(TArray<T> const& val) {
        return "";
        //todo
    }

    template <typename T>
    size_t hash<TArray<T>>::operator()(const TArray<T>& value) const noexcept {
        return 0;
        //todo
        // return rd::contentHashCode(value);
    }
}

template class rd::Polymorphic<FString>;
template class rd::Polymorphic<rd::Wrapper<FString>>;
template struct rd::hash<FString>;
template class rd::Polymorphic<TArray<FString>>;

//endregion
