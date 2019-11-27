#include "BlueprintHighlighter.h"


namespace Jetbrains {
    namespace EditorPlugin {
        
        //companion
        
        //constants
        
        //initializer
        void BlueprintHighlighter::initialize()
        {
        }
        
        //primary ctor
        BlueprintHighlighter::BlueprintHighlighter(int32_t start_, int32_t end_) :
        rd::IPolymorphicSerializable()
        ,start_(std::move(start_)), end_(std::move(end_))
        {
            initialize();
        }
        
        //secondary constructor
        
        //default ctors and dtors
        
        //reader
        BlueprintHighlighter BlueprintHighlighter::read(rd::SerializationCtx& ctx, rd::Buffer & buffer)
        {
            auto start_ = buffer.read_integral<int32_t>();
            auto end_ = buffer.read_integral<int32_t>();
            BlueprintHighlighter res{std::move(start_), std::move(end_)};
            return res;
        }
        
        //writer
        void BlueprintHighlighter::write(rd::SerializationCtx& ctx, rd::Buffer& buffer) const
        {
            buffer.write_integral(start_);
            buffer.write_integral(end_);
        }
        
        //virtual init
        
        //identify
        
        //getters
        int32_t const & BlueprintHighlighter::get_start() const
        {
            return start_;
        }
        int32_t const & BlueprintHighlighter::get_end() const
        {
            return end_;
        }
        
        //intern
        
        //equals trait
        bool BlueprintHighlighter::equals(rd::ISerializable const& object) const
        {
            auto const &other = dynamic_cast<BlueprintHighlighter const&>(object);
            if (this == &other) return true;
            if (this->start_ != other.start_) return false;
            if (this->end_ != other.end_) return false;
            
            return true;
        }
        
        //equality operators
        bool operator==(const BlueprintHighlighter &lhs, const BlueprintHighlighter &rhs) {
            if (lhs.type_name() != rhs.type_name()) return false;
            return lhs.equals(rhs);
        };
        bool operator!=(const BlueprintHighlighter &lhs, const BlueprintHighlighter &rhs){
            return !(lhs == rhs);
        }
        
        //hash code trait
        size_t BlueprintHighlighter::hashCode() const noexcept
        {
            size_t __r = 0;
            __r = __r * 31 + (rd::hash<int32_t>()(get_start()));
            __r = __r * 31 + (rd::hash<int32_t>()(get_end()));
            return __r;
        }
        
        //type name trait
        std::string BlueprintHighlighter::type_name() const
        {
            return "BlueprintHighlighter";
        }
        
        //static type name trait
        std::string BlueprintHighlighter::static_type_name()
        {
            return "BlueprintHighlighter";
        }
        
        //polymorphic to string
        std::string BlueprintHighlighter::toString() const
        {
            std::string res = "BlueprintHighlighter\n";
            res += "\tstart = ";
            res += rd::to_string(start_);
            res += '\n';
            res += "\tend = ";
            res += rd::to_string(end_);
            res += '\n';
            return res;
        }
        
        //external to string
        std::string to_string(const BlueprintHighlighter & value)
        {
            return value.toString();
        }
    };
};
