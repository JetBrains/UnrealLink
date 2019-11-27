#include "UE4Library.h"

#include "../UE4Library/StringRange.h"
#include "../UE4Library/UnrealLogMessage.h"
#include "../UE4Library/BlueprintHighlighter.h"
#include "../UE4Library/BlueprintStruct.h"

#include "../UE4Library/UE4Library.h"
#include "../UE4Library/UE4Library.h"
namespace Jetbrains {
    namespace EditorPlugin {
        
        //companion
        
        UE4Library::UE4LibrarySerializersOwner const UE4Library::serializersOwner;
        
        void UE4Library::UE4LibrarySerializersOwner::registerSerializersCore(rd::Serializers const& serializers) const
        {
            serializers.registry<StringRange>();
            serializers.registry<UnrealLogMessage>();
            serializers.registry<BlueprintHighlighter>();
            serializers.registry<BlueprintStruct>();
        }
        
        void UE4Library::connect(rd::Lifetime lifetime, rd::IProtocol const * protocol)
        {
            UE4Library::serializersOwner.registry(protocol->get_serializers());
            
            identify(*(protocol->get_identity()), rd::RdId::Null().mix("UE4Library"));
            bind(lifetime, protocol, "UE4Library");
        }
        
        
        //constants
        
        //initializer
        void UE4Library::initialize()
        {
            serializationHash = 156269274752715783L;
        }
        
        //primary ctor
        
        //secondary constructor
        
        //default ctors and dtors
        UE4Library::UE4Library()
        {
            initialize();
        }
        
        //reader
        
        //writer
        
        //virtual init
        void UE4Library::init(rd::Lifetime lifetime) const
        {
            rd::RdExtBase::init(lifetime);
        }
        
        //identify
        void UE4Library::identify(const rd::Identities &identities, rd::RdId const &id) const
        {
            rd::RdBindableBase::identify(identities, id);
        }
        
        //getters
        
        //intern
        
        //equals trait
        
        //equality operators
        bool operator==(const UE4Library &lhs, const UE4Library &rhs) {
            return &lhs == &rhs;
        };
        bool operator!=(const UE4Library &lhs, const UE4Library &rhs){
            return !(lhs == rhs);
        }
        
        //hash code trait
        
        //type name trait
        
        //static type name trait
        
        //polymorphic to string
        std::string UE4Library::toString() const
        {
            std::string res = "UE4Library\n";
            return res;
        }
        
        //external to string
        std::string to_string(const UE4Library & value)
        {
            return value.toString();
        }
    };
};
