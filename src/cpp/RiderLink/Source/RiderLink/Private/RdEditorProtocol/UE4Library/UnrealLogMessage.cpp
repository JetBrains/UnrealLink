#include "UnrealLogMessage.h"


namespace Jetbrains {
    namespace EditorPlugin {
        
        //companion
        
        //constants
        
        //initializer
        void UnrealLogMessage::initialize()
        {
        }
        
        //primary ctor
        UnrealLogMessage::UnrealLogMessage(rd::Wrapper<FString> message_, ELogVerbosity::Type type_, rd::Wrapper<FString> category_, rd::optional<rd::DateTime> time_) :
        rd::IPolymorphicSerializable()
        ,message_(std::move(message_)), type_(std::move(type_)), category_(std::move(category_)), time_(std::move(time_))
        {
            initialize();
        }
        
        //secondary constructor
        
        //default ctors and dtors
        
        //reader
        UnrealLogMessage UnrealLogMessage::read(rd::SerializationCtx& ctx, rd::Buffer & buffer)
        {
            auto message_ = rd::Polymorphic<FString>::read(ctx, buffer);
            auto type_ = buffer.read_enum<ELogVerbosity::Type>();
            auto category_ = rd::Polymorphic<FString>::read(ctx, buffer);
            auto time_ = buffer.read_nullable<rd::DateTime>(
            [&ctx, &buffer]() mutable  
            { return buffer.read_date_time(); }
            );
            UnrealLogMessage res{std::move(message_), std::move(type_), std::move(category_), std::move(time_)};
            return res;
        }
        
        //writer
        void UnrealLogMessage::write(rd::SerializationCtx& ctx, rd::Buffer& buffer) const
        {
            rd::Polymorphic<std::decay_t<decltype(message_)>>::write(ctx, buffer, message_);
            buffer.write_enum(type_);
            rd::Polymorphic<std::decay_t<decltype(category_)>>::write(ctx, buffer, category_);
            buffer.write_nullable<rd::DateTime>(time_, 
            [&ctx, &buffer](rd::DateTime const & it) mutable  -> void 
            { buffer.write_date_time(it); }
            );
        }
        
        //virtual init
        
        //identify
        
        //getters
        FString const & UnrealLogMessage::get_message() const
        {
            return *message_;
        }
        ELogVerbosity::Type const & UnrealLogMessage::get_type() const
        {
            return type_;
        }
        FString const & UnrealLogMessage::get_category() const
        {
            return *category_;
        }
        rd::optional<rd::DateTime> const & UnrealLogMessage::get_time() const
        {
            return time_;
        }
        
        //intern
        
        //equals trait
        bool UnrealLogMessage::equals(rd::ISerializable const& object) const
        {
            auto const &other = dynamic_cast<UnrealLogMessage const&>(object);
            if (this == &other) return true;
            if (this->message_ != other.message_) return false;
            if (this->type_ != other.type_) return false;
            if (this->category_ != other.category_) return false;
            if (this->time_ != other.time_) return false;
            
            return true;
        }
        
        //equality operators
        bool operator==(const UnrealLogMessage &lhs, const UnrealLogMessage &rhs) {
            if (lhs.type_name() != rhs.type_name()) return false;
            return lhs.equals(rhs);
        };
        bool operator!=(const UnrealLogMessage &lhs, const UnrealLogMessage &rhs){
            return !(lhs == rhs);
        }
        
        //hash code trait
        size_t UnrealLogMessage::hashCode() const noexcept
        {
            size_t __r = 0;
            __r = __r * 31 + (rd::hash<FString>()(get_message()));
            __r = __r * 31 + (rd::hash<ELogVerbosity::Type>()(get_type()));
            __r = __r * 31 + (rd::hash<FString>()(get_category()));
            __r = __r * 31 + ((static_cast<bool>(get_time())) ? rd::hash<rd::DateTime>()(*get_time()) : 0);
            return __r;
        }
        
        //type name trait
        std::string UnrealLogMessage::type_name() const
        {
            return "UnrealLogMessage";
        }
        
        //static type name trait
        std::string UnrealLogMessage::static_type_name()
        {
            return "UnrealLogMessage";
        }
        
        //polymorphic to string
        std::string UnrealLogMessage::toString() const
        {
            std::string res = "UnrealLogMessage\n";
            res += "\tmessage = ";
            res += rd::to_string(message_);
            res += '\n';
            res += "\ttype = ";
            res += rd::to_string(type_);
            res += '\n';
            res += "\tcategory = ";
            res += rd::to_string(category_);
            res += '\n';
            res += "\ttime = ";
            res += rd::to_string(time_);
            res += '\n';
            return res;
        }
        
        //external to string
        std::string to_string(const UnrealLogMessage & value)
        {
            return value.toString();
        }
    };
};
