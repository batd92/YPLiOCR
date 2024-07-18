# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
project("YPLiOCR")

cmake_minimum_required(VERSION 3.22.1)

# Set the ABI for Android
set(CMAKE_ANDROID_ARCH_ABI "armeabi-v7a;arm64-v8a")

# PaddleLite configuration
set(PaddleLite_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../PaddleLite")
include_directories(${PaddleLite_DIR}/cxx/include)

# OpenCV configuration
set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../OpenCV/sdk/native/jni")
message(STATUS "opencv dir: ${OpenCV_DIR}")
find_package(OpenCV REQUIRED)
message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
include_directories(${OpenCV_INCLUDE_DIRS})

aux_source_directory(. SOURCES)

set(CMAKE_CXX_FLAGS
    "${CMAKE_CXX_FLAGS} -ffast-math -Ofast -Os -DNDEBUG -fno-exceptions -fomit-frame-pointer -fno-asynchronous-unwind-tables -fno-unwind-tables"
)
set(CMAKE_CXX_FLAGS
    "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden -fdata-sections -ffunction-sections -fexceptions"
)
set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-z,nocopyreloc")

add_library(
  # Sets the name of the library.
  Native
  # Sets the library as a shared library.
  SHARED
  # Provides a relative path to your source file(s).
  Native.cc pipeline.cc utils.cc cls_process.cc rec_process.cc det_process.cc db_post_process.cc clipper.cpp)

find_library(
  # Sets the name of the path variable.
  log-lib
  # Specifies the name of the NDK library that you want CMake to locate.
  log)

# PaddleLite shared library
add_library(
        paddle_light_api_shared
        SHARED
        IMPORTED)

set_target_properties(
        paddle_light_api_shared
        PROPERTIES
        IMPORTED_LOCATION
        ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libpaddle_light_api_shared.so
)

# Link libraries
target_link_libraries(
        Native
        paddle_light_api_shared
        ${OpenCV_LIBS}
        GLESv2
        EGL
        jnigraphics
        ${log-lib}
)