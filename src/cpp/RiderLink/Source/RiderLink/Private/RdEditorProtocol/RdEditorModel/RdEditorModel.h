#ifndef RDEDITORMODEL_H
#define RDEDITORMODEL_H

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

#include "../UE4Library/UnrealLogMessage.h"
#include "../UE4Library/BlueprintStruct.h"

#include "UE4TypesMarshallers.h"

#pragma warning( push )
#pragma warning( disable:4250 )
#pragma warning( disable:4307 )
#pragma warning( disable:4267 )
#pragma warning( disable:4244 )
namespace Jetbrains {
    namespace EditorPlugin {
        class RdEditorModel : public rd::RdExtBase
        {
            
            //companion
            
            public:
            struct RdEditorModelSerializersOwner final : public rd::ISerializersOwner {
                void registerSerializersCore(rd::Serializers const& serializers) const override;
            };
            
            static const RdEditorModelSerializersOwner serializersOwner;
            
            
            public:
            void connect(rd::Lifetime lifetime, rd::IProtocol const * protocol);
            
            
            //custom serializers
            private:
            using __IntNullableSerializer = rd::NullableSerializer<rd::Polymorphic<int32_t>>;
            
            //constants
            public:
            
            //fields
            protected:
            rd::RdProperty<rd::optional<int32_t>, RdEditorModel::__IntNullableSerializer> testConnection_;
            rd::RdSignal<UnrealLogMessage, rd::Polymorphic<UnrealLogMessage>> unrealLog_;
            rd::RdProperty<bool, rd::Polymorphic<bool>> play_;
            rd::RdEndpoint<BlueprintStruct, bool, rd::Polymorphic<BlueprintStruct>, rd::Polymorphic<bool>> isBlueprint_;
            rd::RdSignal<BlueprintStruct, rd::Polymorphic<BlueprintStruct>> navigate_;
            
            
            //initializer
            private:
            void initialize();
            
            //primary ctor
            public:
            RdEditorModel(rd::RdProperty<rd::optional<int32_t>, RdEditorModel::__IntNullableSerializer> testConnection_, rd::RdSignal<UnrealLogMessage, rd::Polymorphic<UnrealLogMessage>> unrealLog_, rd::RdProperty<bool, rd::Polymorphic<bool>> play_, rd::RdEndpoint<BlueprintStruct, bool, rd::Polymorphic<BlueprintStruct>, rd::Polymorphic<bool>> isBlueprint_, rd::RdSignal<BlueprintStruct, rd::Polymorphic<BlueprintStruct>> navigate_);
            
            //secondary constructor
            #ifdef __cpp_structured_bindings
                
                //deconstruct trait
            #endif
            
            //default ctors and dtors
            
            RdEditorModel();
            
            RdEditorModel(RdEditorModel &&) = delete;
            
            RdEditorModel& operator=(RdEditorModel &&) = delete;
            
            virtual ~RdEditorModel() = default;
            
            //reader
            
            //writer
            
            //virtual init
            void init(rd::Lifetime lifetime) const override;
            
            //identify
            void identify(const rd::Identities &identities, rd::RdId const &id) const override;
            
            //getters
            rd::IProperty<rd::optional<int32_t>> const & get_testConnection() const;
            rd::ISignal<UnrealLogMessage> const & get_unrealLog() const;
            rd::IProperty<bool> const & get_play() const;
            rd::RdEndpoint<BlueprintStruct, bool> const & get_isBlueprint() const;
            rd::ISignal<BlueprintStruct> const & get_navigate() const;
            
            //intern
            
            //equals trait
            private:
            
            //equality operators
            public:
            friend bool operator==(const RdEditorModel &lhs, const RdEditorModel &rhs);
            friend bool operator!=(const RdEditorModel &lhs, const RdEditorModel &rhs);
            
            //hash code trait
            
            //type name trait
            
            //static type name trait
            
            //polymorphic to string
            private:
            std::string toString() const override;
            
            //external to string
            public:
            friend std::string to_string(const RdEditorModel & value);
        };
    };
};

#pragma warning( pop )


//hash code trait
#ifdef __cpp_structured_bindings
    
    //tuple trait
#endif

#endif // RDEDITORMODEL_H
