cmake_minimum_required(VERSION 3.22.1)

project("edgedetection")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set OpenCV directory
set(OpenCV_DIR ${OPENCV_ANDROID_HOME}/native/jni)

# Find OpenCV
find_package(OpenCV REQUIRED java)

# Include directories
include_directories(${OpenCV_INCLUDE_DIRS})

# Add native library
add_library(
    edgedetection
    SHARED
    native-lib.cpp
    edge_processor.cpp
)

# Link libraries
target_link_libraries(
    edgedetection
    ${OpenCV_LIBS}
    log
    android
    jnigraphics
)

# Compiler-specific options
target_compile_definitions(edgedetection PRIVATE
    VK_USE_PLATFORM_ANDROID_KHR
)