#include "BlueprintStruct.h"


namespace Jetbrains {
    namespace EditorPlugin {
        
        //companion
        
        //constants
        
        //initializer
        void BlueprintStruct::initialize()
        {
        }
        
        //primary ctor
        BlueprintStruct::BlueprintStruct(rd::Wrapper<FString> pathName_, rd::Wrapper<FString> graphName_) :
        rd::IPolymorphicSerializable()
        ,pathName_(std::move(pathName_)), graphName_(std::move(graphName_))
        {
            initialize();
        }
        
        //secondary constructor
        
        //default ctors and dtors
        
        //reader
        BlueprintStruct BlueprintStruct::read(rd::SerializationCtx& ctx, rd::Buffer & buffer)
        {
            auto pathName_ = rd::Polymorphic<FString>::read(ctx, buffer);
            auto graphName_ = rd::Polymorphic<FString>::read(ctx, buffer);
            BlueprintStruct res{std::move(pathName_), std::move(graphName_)};
            return res;
        }
        
        //writer
        void BlueprintStruct::write(rd::SerializationCtx& ctx, rd::Buffer& buffer) const
        {
            rd::Polymorphic<std::decay_t<decltype(pathName_)>>::write(ctx, buffer, pathName_);
            rd::Polymorphic<std::decay_t<decltype(graphName_)>>::write(ctx, buffer, graphName_);
        }
        
        //virtual init
        
        //identify
        
        //getters
        FString const & BlueprintStruct::get_pathName() const
        {
            return *pathName_;
        }
        FString const & BlueprintStruct::get_graphName() const
        {
            return *graphName_;
        }
        
        //intern
        
        //equals trait
        bool BlueprintStruct::equals(rd::ISerializable const& object) const
        {
            auto const &other = dynamic_cast<BlueprintStruct const&>(object);
            if (this == &other) return true;
            if (this->pathName_ != other.pathName_) return false;
            if (this->graphName_ != other.graphName_) return false;
            
            return true;
        }
        
        //equality operators
        bool operator==(const BlueprintStruct &lhs, const BlueprintStruct &rhs) {
            if (lhs.type_name() != rhs.type_name()) return false;
            return lhs.equals(rhs);
        };
        bool operator!=(const BlueprintStruct &lhs, const BlueprintStruct &rhs){
            return !(lhs == rhs);
        }
        
        //hash code trait
        size_t BlueprintStruct::hashCode() const noexcept
        {
            size_t __r = 0;
            __r = __r * 31 + (rd::hash<FString>()(get_pathName()));
            __r = __r * 31 + (rd::hash<FString>()(get_graphName()));
            return __r;
        }
        
        //type name trait
        std::string BlueprintStruct::type_name() const
        {
            return "BlueprintStruct";
        }
        
        //static type name trait
        std::string BlueprintStruct::static_type_name()
        {
            return "BlueprintStruct";
        }
        
        //polymorphic to string
        std::string BlueprintStruct::toString() const
        {
            std::string res = "BlueprintStruct\n";
            res += "\tpathName = ";
            res += rd::to_string(pathName_);
            res += '\n';
            res += "\tgraphName = ";
            res += rd::to_string(graphName_);
            res += '\n';
            return res;
        }
        
        //external to string
        std::string to_string(const BlueprintStruct & value)
        {
            return value.toString();
        }
    };
};
