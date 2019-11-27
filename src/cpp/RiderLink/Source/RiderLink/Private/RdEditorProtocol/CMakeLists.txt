cmake_minimum_required(VERSION 3.7)
option(ENABLE_PCH_HEADERS_FOR_UE4Library "Enable precompiled headers" OFF)
if (ENABLE_PCH_HEADERS_FOR_UE4Library)
    set(PCH_CPP_OPT pch.cpp)
else ()
    set(PCH_CPP_OPT "")
endif ()
add_library(UE4Library STATIC UE4Library/StringRange.cpp
UE4Library/UnrealLogMessage.cpp
UE4Library/BlueprintHighlighter.cpp
UE4Library/BlueprintStruct.cpp
UE4Library/UE4Library.cpp
UE4Library/StringRange.h
UE4Library/UnrealLogMessage.h
UE4Library/BlueprintHighlighter.h
UE4Library/BlueprintStruct.h
UE4Library/UE4Library.h
${PCH_CPP_OPT})

target_include_directories(UE4Library PUBLIC ${CMAKE_CURRENT_SOURCE_DIR} UE4Library)
target_link_libraries(UE4Library PUBLIC rd_framework_cpp)
if (ENABLE_PCH_HEADERS_FOR_UE4Library)
    include(PrecompiledHeader.cmake)
    add_precompiled_header(UE4Library pch.h SOURCE_CXX pch.cpp FORCEINCLUDE)
endif ()
