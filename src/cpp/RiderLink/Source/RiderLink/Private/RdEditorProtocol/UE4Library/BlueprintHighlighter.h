#ifndef BLUEPRINTHIGHLIGHTER_H
#define BLUEPRINTHIGHLIGHTER_H

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

#include "UE4TypesMarshallers.h"

#pragma warning( push )
#pragma warning( disable:4250 )
#pragma warning( disable:4307 )
#pragma warning( disable:4267 )
#pragma warning( disable:4244 )
namespace Jetbrains {
    namespace EditorPlugin {
        
        //data
        class BlueprintHighlighter : public rd::IPolymorphicSerializable
        {
            
            //companion
            
            //custom serializers
            private:
            
            //constants
            public:
            
            //fields
            protected:
            int32_t start_;
            int32_t end_;
            
            
            //initializer
            private:
            void initialize();
            
            //primary ctor
            public:
            BlueprintHighlighter(int32_t start_, int32_t end_);
            
            //secondary constructor
            #ifdef __cpp_structured_bindings
                
                //deconstruct trait
                template <size_t I>
                decltype(auto) get() const
                {
                    if constexpr (I < 0 || I >= 2) static_assert (I < 0 || I >= 2, "I < 0 || I >= 2");
                    else if constexpr (I==0)  return static_cast<const int32_t&>(get_start());
                    else if constexpr (I==1)  return static_cast<const int32_t&>(get_end());
                }
            #endif
            
            //default ctors and dtors
            
            BlueprintHighlighter() = delete;
            
            BlueprintHighlighter(BlueprintHighlighter const &) = default;
            
            BlueprintHighlighter& operator=(BlueprintHighlighter const &) = default;
            
            BlueprintHighlighter(BlueprintHighlighter &&) = default;
            
            BlueprintHighlighter& operator=(BlueprintHighlighter &&) = default;
            
            virtual ~BlueprintHighlighter() = default;
            
            //reader
            static BlueprintHighlighter read(rd::SerializationCtx& ctx, rd::Buffer & buffer);
            
            //writer
            void write(rd::SerializationCtx& ctx, rd::Buffer& buffer) const override;
            
            //virtual init
            
            //identify
            
            //getters
            int32_t const & get_start() const;
            int32_t const & get_end() const;
            
            //intern
            
            //equals trait
            private:
            bool equals(rd::ISerializable const& object) const override;
            
            //equality operators
            public:
            friend bool operator==(const BlueprintHighlighter &lhs, const BlueprintHighlighter &rhs);
            friend bool operator!=(const BlueprintHighlighter &lhs, const BlueprintHighlighter &rhs);
            
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
            friend std::string to_string(const BlueprintHighlighter & value);
        };
    };
};

#pragma warning( pop )


//hash code trait
namespace rd {
    template <> struct hash<Jetbrains::EditorPlugin::BlueprintHighlighter> {
        size_t operator()(const Jetbrains::EditorPlugin::BlueprintHighlighter & value) const noexcept {
            return value.hashCode();
        }
    };
}
#ifdef __cpp_structured_bindings
    
    //tuple trait
    namespace std {
        template<>
        class tuple_size<Jetbrains::EditorPlugin::BlueprintHighlighter> : public integral_constant<size_t, 2> {};
        
        template<size_t I>
        class std::tuple_element<I, Jetbrains::EditorPlugin::BlueprintHighlighter> {
        public:
            using type = decltype (declval<Jetbrains::EditorPlugin::BlueprintHighlighter>().get<I>());
        };
    };
#endif

#endif // BLUEPRINTHIGHLIGHTER_H
