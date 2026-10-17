cmake_minimum_required(VERSION 3.22)
project(pitchee_android LANGUAGES C CXX)

if(NOT PITCHEE_CORE_DIR)
    message(FATAL_ERROR "PITCHEE_CORE_DIR is required")
endif()
if(NOT PITCHEE_NCNN_ROOT)
    message(FATAL_ERROR "PITCHEE_NCNN_ROOT is required")
endif()

set(PITCHEE_BUILD_SHARED ON CACHE BOOL "" FORCE)
set(PITCHEE_BUILD_CLI OFF CACHE BOOL "" FORCE)
set(PITCHEE_BUILD_TESTS OFF CACHE BOOL "" FORCE)

# Android inference is ncnn-only; ONNX Runtime is not linked or packaged.
set(PITCHEE_ENABLE_ORT OFF CACHE BOOL "" FORCE)
set(PITCHEE_ENABLE_NCNN ON CACHE BOOL "" FORCE)
set(PITCHEE_NCNN_INCLUDE_DIR
    "${PITCHEE_NCNN_ROOT}/${ANDROID_ABI}/include"
    CACHE PATH "" FORCE
)
set(PITCHEE_NCNN_LIBRARY
    "${PITCHEE_NCNN_ROOT}/${ANDROID_ABI}/lib/libncnn.a"
    CACHE FILEPATH "" FORCE
)

add_subdirectory("${PITCHEE_CORE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/pitchee-core")

add_library(pitchee_core_jni SHARED PitcheeCoreJNI.cpp)
target_compile_features(pitchee_core_jni PRIVATE cxx_std_17)
target_link_libraries(pitchee_core_jni PRIVATE pitchee_core)

# Core links -fopenmp. Both libraries are shared, so link the OpenMP runtime in
# statically instead of shipping an extra libomp.so with the app.
target_link_options(pitchee_core PRIVATE -static-openmp)

# Android 15+ devices may use 16 KB memory pages.
target_link_options(pitchee_core PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(pitchee_core_jni PRIVATE "-Wl,-z,max-page-size=16384")

# Inference is unusably slow in an -O0 debug build. Keep Java/Kotlin
# debugging intact while optimizing the native model code.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(pitchee_core PRIVATE -O2)
    target_compile_options(pitchee_core_jni PRIVATE -O2)
endif()
