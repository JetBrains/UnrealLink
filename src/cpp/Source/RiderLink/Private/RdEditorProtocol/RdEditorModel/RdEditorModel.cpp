#include "RdEditorModel.h"

#include "UE4TypesMarshallers.h"

#include "../RdEditorRoot/RdEditorRoot.h"
namespace Jetbrains {
    namespace EditorPlugin {
        
        //companion
        
        RdEditorModel::RdEditorModelSerializersOwner const RdEditorModel::serializersOwner;
        
        void RdEditorModel::RdEditorModelSerializersOwner::registerSerializersCore(rd::Serializers const& serializers) const
        {
        }
        
        void RdEditorModel::connect(rd::Lifetime lifetime, rd::IProtocol const * protocol)
        {
            RdEditorRoot::serializersOwner.registry(protocol->get_serializers());
            
            identify(*(protocol->get_identity()), rd::RdId::Null().mix("RdEditorModel"));
            bind(lifetime, protocol, "RdEditorModel");
        }
        
        
        //constants
        
        //initializer
        void RdEditorModel::initialize()
        {
            testConnection_.optimize_nested = true;
            play_.optimize_nested = true;
            testConnection_.is_master = false;
            play_.is_master = false;
            isBlueprint_.async = true;
            serializationHash = 7725330141921354411L;
        }
        
        //primary ctor
        RdEditorModel::RdEditorModel(rd::RdProperty<rd::optional<int32_t>, RdEditorModel::__IntNullableSerializer> testConnection_, rd::RdSignal<UnrealLogMessage, rd::Polymorphic<UnrealLogMessage>> unrealLog_, rd::RdProperty<bool, rd::Polymorphic<bool>> play_, rd::RdEndpoint<BlueprintStruct, bool, rd::Polymorphic<BlueprintStruct>, rd::Polymorphic<bool>> isBlueprint_, rd::RdSignal<BlueprintStruct, rd::Polymorphic<BlueprintStruct>> navigate_) :
        rd::RdExtBase()
        ,testConnection_(std::move(testConnection_)), unrealLog_(std::move(unrealLog_)), play_(std::move(play_)), isBlueprint_(std::move(isBlueprint_)), navigate_(std::move(navigate_))
        {
            initialize();
        }
        
        //secondary constructor
        
        //default ctors and dtors
        RdEditorModel::RdEditorModel()
        {
            initialize();
        }
        
        //reader
        
        //writer
        
        //virtual init
        void RdEditorModel::init(rd::Lifetime lifetime) const
        {
            rd::RdExtBase::init(lifetime);
            bindPolymorphic(testConnection_, lifetime, this, "testConnection");
            bindPolymorphic(unrealLog_, lifetime, this, "unrealLog");
            bindPolymorphic(play_, lifetime, this, "play");
            bindPolymorphic(isBlueprint_, lifetime, this, "isBlueprint");
            bindPolymorphic(navigate_, lifetime, this, "navigate");
        }
        
        //identify
        void RdEditorModel::identify(const rd::Identities &identities, rd::RdId const &id) const
        {
            rd::RdBindableBase::identify(identities, id);
            identifyPolymorphic(testConnection_, identities, id.mix(".testConnection"));
            identifyPolymorphic(unrealLog_, identities, id.mix(".unrealLog"));
            identifyPolymorphic(play_, identities, id.mix(".play"));
            identifyPolymorphic(isBlueprint_, identities, id.mix(".isBlueprint"));
            identifyPolymorphic(navigate_, identities, id.mix(".navigate"));
        }
        
        //getters
        rd::IProperty<rd::optional<int32_t>> const & RdEditorModel::get_testConnection() const
        {
            return testConnection_;
        }
        rd::ISignal<UnrealLogMessage> const & RdEditorModel::get_unrealLog() const
        {
            return unrealLog_;
        }
        rd::IProperty<bool> const & RdEditorModel::get_play() const
        {
            return play_;
        }
        rd::RdEndpoint<BlueprintStruct, bool> const & RdEditorModel::get_isBlueprint() const
        {
            return isBlueprint_;
        }
        rd::ISignal<BlueprintStruct> const & RdEditorModel::get_navigate() const
        {
            return navigate_;
        }
        
        //intern
        
        //equals trait
        
        //equality operators
        bool operator==(const RdEditorModel &lhs, const RdEditorModel &rhs) {
            return &lhs == &rhs;
        };
        bool operator!=(const RdEditorModel &lhs, const RdEditorModel &rhs){
            return !(lhs == rhs);
        }
        
        //hash code trait
        
        //type name trait
        
        //static type name trait
        
        //polymorphic to string
        std::string RdEditorModel::toString() const
        {
            std::string res = "RdEditorModel\n";
            res += "\ttestConnection = ";
            res += rd::to_string(testConnection_);
            res += '\n';
            res += "\tunrealLog = ";
            res += rd::to_string(unrealLog_);
            res += '\n';
            res += "\tplay = ";
            res += rd::to_string(play_);
            res += '\n';
            res += "\tisBlueprint = ";
            res += rd::to_string(isBlueprint_);
            res += '\n';
            res += "\tnavigate = ";
            res += rd::to_string(navigate_);
            res += '\n';
            return res;
        }
        
        //external to string
        std::string to_string(const RdEditorModel & value)
        {
            return value.toString();
        }
    };
};
