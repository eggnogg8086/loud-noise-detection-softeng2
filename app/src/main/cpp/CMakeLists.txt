cmake_minimum_required(VERSION 3.22.1)
project(noisecapture)

# Download Oboe via FetchContent
include(FetchContent)
FetchContent_Declare(
        oboe
        GIT_REPOSITORY https://github.com/google/oboe.git
        GIT_TAG 1.8.0
)
FetchContent_MakeAvailable(oboe)

# KissFFT — just two files, include directly
add_library(kissfft STATIC
        kissfft/kiss_fft.c
        kissfft/kiss_fftr.c
)
target_include_directories(kissfft PUBLIC kissfft)

add_library(noisecapture SHARED
        AudioEngine.cpp
        AudioBridge.cpp
)

target_include_directories(noisecapture PRIVATE
        ${oboe_SOURCE_DIR}/include
        kissfft
)

target_link_libraries(noisecapture
        oboe
        kissfft
        android
        log
)