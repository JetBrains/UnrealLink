#ifndef BLUEPRINTSTRUCT_H
#define BLUEPRINTSTRUCT_H

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

#include "Containers/UnrealString.h"

#include "UE4TypesMarshallers.h"

#pragma warning( push )
#pragma warning( disable:4250 )
#pragma warning( disable:4307 )
#pragma warning( disable:4267 )
#pragma warning( disable:4244 )
namespace Jetbrains {
    namespace EditorPlugin {
        
        //data
        class BlueprintStruct : public rd::IPolymorphicSerializable
        {
            
            //companion
            
            //custom serializers
            private:
            
            //constants
            public:
            
            //fields
            protected:
            rd::Wrapper<FString> pathName_;
            rd::Wrapper<FString> graphName_;
            
            
            //initializer
            private:
            void initialize();
            
            //primary ctor
            public:
            BlueprintStruct(rd::Wrapper<FString> pathName_, rd::Wrapper<FString> graphName_);
            
            //secondary constructor
            #ifdef __cpp_structured_bindings
                
                //deconstruct trait
                template <size_t I>
                decltype(auto) get() const
                {
                    if constexpr (I < 0 || I >= 2) static_assert (I < 0 || I >= 2, "I < 0 || I >= 2");
                    else if constexpr (I==0)  return static_cast<const FString&>(get_pathName());
                    else if constexpr (I==1)  return static_cast<const FString&>(get_graphName());
                }
            #endif
            
            //default ctors and dtors
            
            BlueprintStruct() = delete;
            
            BlueprintStruct(BlueprintStruct const &) = default;
            
            BlueprintStruct& operator=(BlueprintStruct const &) = default;
            
            BlueprintStruct(BlueprintStruct &&) = default;
            
            BlueprintStruct& operator=(BlueprintStruct &&) = default;
            
            virtual ~BlueprintStruct() = default;
            
            //reader
            static BlueprintStruct read(rd::SerializationCtx& ctx, rd::Buffer & buffer);
            
            //writer
            void write(rd::SerializationCtx& ctx, rd::Buffer& buffer) const override;
            
            //virtual init
            
            //identify
            
            //getters
            FString const & get_pathName() const;
            FString const & get_graphName() const;
            
            //intern
            
            //equals trait
            private:
            bool equals(rd::ISerializable const& object) const override;
            
            //equality operators
            public:
            friend bool operator==(const BlueprintStruct &lhs, const BlueprintStruct &rhs);
            friend bool operator!=(const BlueprintStruct &lhs, const BlueprintStruct &rhs);
            
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
            friend std::string to_string(const BlueprintStruct & value);
        };
    };
};

#pragma warning( pop )


//hash code trait
namespace rd {
    template <> struct hash<Jetbrains::EditorPlugin::BlueprintStruct> {
        size_t operator()(const Jetbrains::EditorPlugin::BlueprintStruct & value) const noexcept {
            return value.hashCode();
        }
    };
}
#ifdef __cpp_structured_bindings
    
    //tuple trait
    namespace std {
        template<>
        class tuple_size<Jetbrains::EditorPlugin::BlueprintStruct> : public integral_constant<size_t, 2> {};
        
        template<size_t I>
        class std::tuple_element<I, Jetbrains::EditorPlugin::BlueprintStruct> {
        public:
            using type = decltype (declval<Jetbrains::EditorPlugin::BlueprintStruct>().get<I>());
        };
    };
#endif

#endif // BLUEPRINTSTRUCT_H
