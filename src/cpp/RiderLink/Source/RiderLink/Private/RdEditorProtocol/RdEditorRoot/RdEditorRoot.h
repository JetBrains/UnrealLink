#ifndef RDEDITORROOT_H
#define RDEDITORROOT_H

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
        class RdEditorRoot : public rd::RdExtBase
        {
            
            //companion
            
            public:
            struct RdEditorRootSerializersOwner final : public rd::ISerializersOwner {
                void registerSerializersCore(rd::Serializers const& serializers) const override;
            };
            
            static const RdEditorRootSerializersOwner serializersOwner;
            
            
            public:
            void connect(rd::Lifetime lifetime, rd::IProtocol const * protocol);
            
            
            //custom serializers
            private:
            
            //constants
            public:
            
            //fields
            protected:
            
            //initializer
            private:
            void initialize();
            
            //primary ctor
            public:
            
            //secondary constructor
            #ifdef __cpp_structured_bindings
                
                //deconstruct trait
            #endif
            
            //default ctors and dtors
            
            RdEditorRoot();
            
            RdEditorRoot(RdEditorRoot &&) = delete;
            
            RdEditorRoot& operator=(RdEditorRoot &&) = delete;
            
            virtual ~RdEditorRoot() = default;
            
            //reader
            
            //writer
            
            //virtual init
            void init(rd::Lifetime lifetime) const override;
            
            //identify
            void identify(const rd::Identities &identities, rd::RdId const &id) const override;
            
            //getters
            
            //intern
            
            //equals trait
            private:
            
            //equality operators
            public:
            friend bool operator==(const RdEditorRoot &lhs, const RdEditorRoot &rhs);
            friend bool operator!=(const RdEditorRoot &lhs, const RdEditorRoot &rhs);
            
            //hash code trait
            
            //type name trait
            
            //static type name trait
            
            //polymorphic to string
            private:
            std::string toString() const override;
            
            //external to string
            public:
            friend std::string to_string(const RdEditorRoot & value);
        };
    };
};

#pragma warning( pop )


//hash code trait
#ifdef __cpp_structured_bindings
    
    //tuple trait
#endif

#endif // RDEDITORROOT_H