cmake_minimum_required(VERSION 3.7)
option(ENABLE_PCH_HEADERS_FOR_RdEditorRoot "Enable precompiled headers" OFF)
if (ENABLE_PCH_HEADERS_FOR_RdEditorRoot)
    set(PCH_CPP_OPT pch_RdEditorRoot.cpp)
else ()
    set(PCH_CPP_OPT "")
endif ()
add_library(RdEditorRoot STATIC RdEditorModel/RdEditorModel.cpp
RdEditorModel/RdEditorModel.h
RdEditorRoot/RdEditorRoot.cpp
RdEditorRoot/RdEditorRoot.h
instantiations.h
instantiations.cpp
${PCH_CPP_OPT})

target_include_directories(RdEditorRoot PUBLIC ${CMAKE_CURRENT_SOURCE_DIR} RdEditorModel
RdEditorRoot)
target_link_libraries(RdEditorRoot PUBLIC rd_framework_cpp)
if (ENABLE_PCH_HEADERS_FOR_RdEditorRoot)
    include(PrecompiledHeader.cmake)
    add_precompiled_header(RdEditorRoot pch_RdEditorRoot.h SOURCE_CXX pch_RdEditorRoot.cpp FORCEINCLUDE)
endif ()
