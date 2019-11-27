#include "StringRange.h"


namespace Jetbrains {
    namespace EditorPlugin {
        
        //companion
        
        //constants
        
        //initializer
        void StringRange::initialize()
        {
        }
        
        //primary ctor
        StringRange::StringRange(int32_t left_, int32_t right_) :
        rd::IPolymorphicSerializable()
        ,left_(std::move(left_)), right_(std::move(right_))
        {
            initialize();
        }
        
        //secondary constructor
        
        //default ctors and dtors
        
        //reader
        StringRange StringRange::read(rd::SerializationCtx& ctx, rd::Buffer & buffer)
        {
            auto left_ = buffer.read_integral<int32_t>();
            auto right_ = buffer.read_integral<int32_t>();
            StringRange res{std::move(left_), std::move(right_)};
            return res;
        }
        
        //writer
        void StringRange::write(rd::SerializationCtx& ctx, rd::Buffer& buffer) const
        {
            buffer.write_integral(left_);
            buffer.write_integral(right_);
        }
        
        //virtual init
        
        //identify
        
        //getters
        int32_t const & StringRange::get_left() const
        {
            return left_;
        }
        int32_t const & StringRange::get_right() const
        {
            return right_;
        }
        
        //intern
        
        //equals trait
        bool StringRange::equals(rd::ISerializable const& object) const
        {
            auto const &other = dynamic_cast<StringRange const&>(object);
            if (this == &other) return true;
            if (this->left_ != other.left_) return false;
            if (this->right_ != other.right_) return false;
            
            return true;
        }
        
        //equality operators
        bool operator==(const StringRange &lhs, const StringRange &rhs) {
            if (lhs.type_name() != rhs.type_name()) return false;
            return lhs.equals(rhs);
        };
        bool operator!=(const StringRange &lhs, const StringRange &rhs){
            return !(lhs == rhs);
        }
        
        //hash code trait
        size_t StringRange::hashCode() const noexcept
        {
            size_t __r = 0;
            __r = __r * 31 + (rd::hash<int32_t>()(get_left()));
            __r = __r * 31 + (rd::hash<int32_t>()(get_right()));
            return __r;
        }
        
        //type name trait
        std::string StringRange::type_name() const
        {
            return "StringRange";
        }
        
        //static type name trait
        std::string StringRange::static_type_name()
        {
            return "StringRange";
        }
        
        //polymorphic to string
        std::string StringRange::toString() const
        {
            std::string res = "StringRange\n";
            res += "\tleft = ";
            res += rd::to_string(left_);
            res += '\n';
            res += "\tright = ";
            res += rd::to_string(right_);
            res += '\n';
            return res;
        }
        
        //external to string
        std::string to_string(const StringRange & value)
        {
            return value.toString();
        }
    };
};
