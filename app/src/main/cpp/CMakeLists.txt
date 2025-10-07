cmake_minimum_required(VERSION 3.22.1)

project("edgedetection")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Use libc++
set(ANDROID_STL "c++_shared")

# Use OpenCV SDK's CMake config
find_package(OpenCV 4.12 REQUIRED java)

# Add native library
add_library(
    edgedetection
    SHARED
    native-lib.cpp
    edge_processor.cpp
)

# Link libraries using OpenCV SDK
target_link_libraries(
    edgedetection
    ${OpenCV_LIBRARIES}
    c++_shared
    log
    android
    jnigraphics
)

# Ensure C++ shared library is used
set_target_properties(edgedetection PROPERTIES
    ANDROID_STL c++_shared
)

# Compiler-specific options
target_compile_definitions(edgedetection PRIVATE
    VK_USE_PLATFORM_ANDROID_KHR
)