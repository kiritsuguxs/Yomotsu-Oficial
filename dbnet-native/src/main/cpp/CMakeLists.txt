cmake_minimum_required(VERSION 3.22.1)
project(yomotsu_dbnet LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Experimental DBNet runtime is ARM64 only")
endif()
include(FetchContent)
# Only the ncnn dependency directory is linked. No Houri Kotlin, OCR, renderer,
# inpainting model, or complete engine module enters this Android library.
FetchContent_Declare(dbnet_runtime
    URL https://codeload.github.com/PineappleTwilight/houri-engine/tar.gz/85351aa3822fe2611f68cfd092972e6ac573f203
    URL_HASH SHA256=21453724e54542081b32fa8b8e97bd8a0cc8f67513ed3ddebcd42faec5a5fbb2
)
FetchContent_GetProperties(dbnet_runtime)
if(NOT dbnet_runtime_POPULATED)
    FetchContent_Populate(dbnet_runtime)
endif()
set(ncnn_DIR "${dbnet_runtime_SOURCE_DIR}/engine/src/main/cpp/ncnn/lib/cmake/ncnn")
find_package(ncnn REQUIRED)
add_library(yomotsu_dbnet SHARED dbnet_jni.cpp)
target_link_libraries(yomotsu_dbnet ncnn log)
target_link_options(yomotsu_dbnet PRIVATE "-Wl,-z,max-page-size=16384")
