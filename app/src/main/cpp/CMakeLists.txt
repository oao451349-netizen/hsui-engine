cmake_minimum_required(VERSION 3.22.1)
project(frame_processor)

# ── OpenCV ────────────────────────────────────────────────────────────────────
# Set OPENCV_DIR to your local OpenCV Android SDK path, e.g.:
# cmake -DOPENCV_DIR=/path/to/OpenCV-android-sdk/sdk/native/jni ..
set(OPENCV_DIR "" CACHE PATH "Path to OpenCV Android SDK native/jni directory")
if(NOT OPENCV_DIR STREQUAL "")
    include("${OPENCV_DIR}/OpenCVConfig.cmake")
endif()

# ── NDK system libraries ──────────────────────────────────────────────────────
find_library(log-lib      log)
find_library(android-lib  android)
find_library(mediandk-lib mediandk)
find_library(jnigraphics-lib jnigraphics)

# ── Shared library ────────────────────────────────────────────────────────────
add_library(
    frame_processor
    SHARED
    frame_processor.cpp
)

target_include_directories(frame_processor PRIVATE
    ${OPENCV_DIR}/include
)

target_link_libraries(frame_processor
    ${log-lib}
    ${android-lib}
    ${mediandk-lib}
    ${jnigraphics-lib}
    opencv_core
    opencv_imgproc
)
