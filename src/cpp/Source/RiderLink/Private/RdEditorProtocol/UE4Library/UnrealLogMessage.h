#ifndef UNREALLOGMESSAGE_H
#define UNREALLOGMESSAGE_H

#include "Protocol.h"
#include "types/DateTime.h"
#include "RdSignal.h"
#include "RdProperty.h"
#include "RdList.h"
#include "RdSet.h"
#include "RdMap.h"
#include "ISerializable.h"
#include "ISerializersOwner.h"
#include "IUnknownInstance.h"
#include "Polymorphic.h"
#include "NullableSerializer.h"
#include "ArraySerializer.h"
#include "InternedSerializer.h"
#include "SerializationCtx.h"
#include "Serializers.h"
#include "RdExtBase.h"
#include "RdCall.h"
#include "RdEndpoint.h"
#include "RdSymmetricCall.h"
#include "std/to_string.h"
#include "std/hash.h"
#include "enum.h"
#include "gen_util.h"

#include <cstring>
#include <cstdint>
#include <vector>
#include <ctime>

#include "thirdparty.hpp"

#include "Runtime/Core/Public/Containers/UnrealString.h"
#include "Logging/LogVerbosity.h"

#include "UE4TypesMarshallers.h"

#pragma warning( push )
#pragma warning( disable:4250 )
#pragma warning( disable:4307 )
#pragma warning( disable:4267 )
#pragma warning( disable:4244 )
namespace Jetbrains {
    namespace EditorPlugin {
        
        //data
        class UnrealLogMessage : public rd::IPolymorphicSerializable
        {
            
            //companion
            
            //custom serializers
            private:
            
            //constants
            public:
            
            //fields
            protected:
            rd::Wrapper<FString> message_;
            ELogVerbosity::Type type_;
            rd::Wrapper<FString> category_;
            rd::optional<rd::DateTime> time_;
            
            
            //initializer
            private:
            void initialize();
            
            //primary ctor
            public:
            UnrealLogMessage(rd::Wrapper<FString> message_, ELogVerbosity::Type type_, rd::Wrapper<FString> category_, rd::optional<rd::DateTime> time_);
            
            //secondary constructor
            #ifdef __cpp_structured_bindings
                
                //deconstruct trait
                template <size_t I>
                decltype(auto) get() const
                {
                    if constexpr (I < 0 || I >= 4) static_assert (I < 0 || I >= 4, "I < 0 || I >= 4");
                    else if constexpr (I==0)  return static_cast<const FString&>(get_message());
                    else if constexpr (I==1)  return static_cast<const ELogVerbosity::Type&>(get_type());
                    else if constexpr (I==2)  return static_cast<const FString&>(get_category());
                    else if constexpr (I==3)  return static_cast<const rd::optional<rd::DateTime>&>(get_time());
                }
            #endif
            
            //default ctors and dtors
            
            UnrealLogMessage() = delete;
            
            UnrealLogMessage(UnrealLogMessage const &) = default;
            
            UnrealLogMessage& operator=(UnrealLogMessage const &) = default;
            
            UnrealLogMessage(UnrealLogMessage &&) = default;
            
            UnrealLogMessage& operator=(UnrealLogMessage &&) = default;
            
            virtual ~UnrealLogMessage() = default;
            
            //reader
            static UnrealLogMessage read(rd::SerializationCtx& ctx, rd::Buffer & buffer);
            
            //writer
            void write(rd::SerializationCtx& ctx, rd::Buffer& buffer) const override;
            
            //virtual init
            
            //identify
            
            //getters
            FString const & get_message() const;
            ELogVerbosity::Type const & get_type() const;
            FString const & get_category() const;
            rd::optional<rd::DateTime> const & get_time() const;
            
            //intern
            
            //equals trait
            private:
            bool equals(rd::ISerializable const& object) const override;
            
            //equality operators
            public:
            friend bool operator==(const UnrealLogMessage &lhs, const UnrealLogMessage &rhs);
            friend bool operator!=(const UnrealLogMessage &lhs, const UnrealLogMessage &rhs);
            
            //hash code trait
            size_t hashCode() const noexcept override;
            
            //type name trait
            std::string type_name() const override;
            
            //static type name trait
            static std::string static_type_name();
            
            //polymorphic to string
            private:
            std::string toString() const override;
            
            //external to string
            public:
            friend std::string to_string(const UnrealLogMessage & value);
        };
    };
};

#pragma warning( pop )


//hash code trait
namespace rd {
    template <> struct hash<Jetbrains::EditorPlugin::UnrealLogMessage> {
        size_t operator()(const Jetbrains::EditorPlugin::UnrealLogMessage & value) const noexcept {
            return value.hashCode();
        }
    };
}
#ifdef __cpp_structured_bindings
    
    //tuple trait
    namespace std {
        template<>
        class tuple_size<Jetbrains::EditorPlugin::UnrealLogMessage> : public integral_constant<size_t, 4> {};
        
        template<size_t I>
        class std::tuple_element<I, Jetbrains::EditorPlugin::UnrealLogMessage> {
        public:
            using type = decltype (declval<Jetbrains::EditorPlugin::UnrealLogMessage>().get<I>());
        };
    };
#endif

#endif // UNREALLOGMESSAGE_H
