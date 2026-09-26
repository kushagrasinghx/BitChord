# The Automix analyser, built for the desktop.
#
# Deliberately the same sources the Android build compiles: `native/analyzer`
# is already platform-neutral C++17 with no dependency beyond the standard
# library, and `app/src/main/cpp/jni` is plain JNI with nothing Android about
# it. Reusing both verbatim is what keeps the desktop's tempo, beat grid and
# mel front end bit-identical to the source of truth rather than a second
# implementation that drifts.
#
# The JNI symbol names encode the Java package, which is why the desktop's
# analysis classes live in `com.music.bitchord.playback.smart` exactly as
# Android's do.

cmake_minimum_required(VERSION 3.16)

if(POLICY CMP0091)
    cmake_policy(SET CMP0091 NEW)
endif()

project(bitchord_analysis LANGUAGES CXX)

set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

get_filename_component(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)

foreach(suffix "" _DEBUG _RELEASE _RELWITHDEBINFO _MINSIZEREL)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY${suffix} ${CMAKE_BINARY_DIR})
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY${suffix} ${CMAKE_BINARY_DIR})
endforeach()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_library(bitchord_analysis SHARED
    ${REPO_ROOT}/native/analyzer/audio_analysis.cpp
    ${REPO_ROOT}/native/analyzer/tempo_analysis.cpp
    ${REPO_ROOT}/native/analyzer/resampler.cpp
    ${REPO_ROOT}/native/analyzer/mel_spectrogram.cpp
    ${REPO_ROOT}/native/analyzer/vocal_spectrogram.cpp
    ${REPO_ROOT}/app/src/main/cpp/jni/analysis_jni.cpp
    ${REPO_ROOT}/app/src/main/cpp/jni/mel_jni.cpp
    ${REPO_ROOT}/app/src/main/cpp/jni/vocal_jni.cpp
)

target_include_directories(bitchord_analysis PRIVATE
    ${REPO_ROOT}/native
    ${REPO_ROOT}/app/src/main/cpp
)

# JNI headers come from the JDK running the build rather than from find_package,
# which on a JDK with no JAVA_HOME set reports a stale system install.
if(DEFINED ENV{JAVA_HOME})
    target_include_directories(bitchord_analysis PRIVATE
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/linux
        $ENV{JAVA_HOME}/include/win32
        $ENV{JAVA_HOME}/include/darwin
    )
else()
    find_package(JNI REQUIRED)
    target_include_directories(bitchord_analysis PRIVATE ${JNI_INCLUDE_DIRS})
endif()

# Cross-building for Windows, the one case where the JDK running the build has
# no header for the platform being built: `jni.h` is shared, `jni_md.h` is not.
# Listed after the JDK's own paths so a real Windows JDK always wins.
if(CMAKE_SYSTEM_NAME STREQUAL "Windows" AND NOT CMAKE_HOST_WIN32)
    target_include_directories(bitchord_analysis PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/win32)
endif()

target_compile_options(bitchord_analysis PRIVATE -O2)

# `System.mapLibraryName` asks for `bitchord_analysis.dll` on Windows, with no
# prefix — but MinGW's CMake defaults to the Unix `lib` one, so a cross-built
# library came out as `libbitchord_analysis.dll` and the loader never found it.
# Set on the target rather than in the toolchain file, because `project()`
# reinitialises CMAKE_SHARED_LIBRARY_PREFIX per language and overwrites
# anything the toolchain set beforehand.
if(WIN32)
    set_target_properties(bitchord_analysis PROPERTIES PREFIX "")
endif()

# The Windows media controls, which are WinRT and so buildable only by MSVC —
# MinGW has no C++/WinRT. A Linux cross-build therefore produces the analyser
# and no SMTC; the Windows CI job, which builds on Windows, produces both.
if(MSVC)
    add_library(bitchord_smtc SHARED ${CMAKE_CURRENT_SOURCE_DIR}/smtc/smtc_jni.cpp)
    # C++20 rather than the 17 the analyser uses: under 17, C++/WinRT reaches for
    # <experimental/coroutine>, and current MSVC refuses to compile that header
    # at all. C++20 gives it the standard <coroutine> instead.
    set_target_properties(bitchord_smtc PROPERTIES PREFIX "" CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)
    if(DEFINED ENV{JAVA_HOME})
        target_include_directories(bitchord_smtc PRIVATE $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/win32)
    else()
        find_package(JNI REQUIRED)
        target_include_directories(bitchord_smtc PRIVATE ${JNI_INCLUDE_DIRS})
    endif()
    # The WinRT umbrella import library.
    target_link_libraries(bitchord_smtc PRIVATE windowsapp gdi32)
endif()
