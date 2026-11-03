# Native analysis front end for Automix.
#
# Phase 1: whole-track DSP analysis (tempo/key/energy/structure via
# autocorrelation, no ML model). Phase 2 adds the log-mel front end the
# Beat This! ONNX beat/downbeat model reads (mel_spectrogram.cpp / mel_jni.cpp).
# Phase 3 adds the linear-frequency STFT the open-unmix vocal-separation model
# reads (vocal_spectrogram.cpp / vocal_jni.cpp). Both models themselves run
# through ONNX Runtime on the Kotlin side, not here. The shared analyzer
# sources live under the repo's root `native/` tree rather than here, so a
# later phase adding a Rubber Band-based transition renderer can add sources
# to that same tree without duplicating this module.

cmake_minimum_required(VERSION 3.22.1)
project(bitchord_analysis LANGUAGES C CXX)

get_filename_component(BITCHORD_NATIVE_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../native"
    ABSOLUTE)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(bitchord_analysis SHARED
    ${BITCHORD_NATIVE_DIR}/analyzer/audio_analysis.cpp
    ${BITCHORD_NATIVE_DIR}/analyzer/tempo_analysis.cpp
    ${BITCHORD_NATIVE_DIR}/analyzer/resampler.cpp
    ${BITCHORD_NATIVE_DIR}/analyzer/mel_spectrogram.cpp
    ${BITCHORD_NATIVE_DIR}/analyzer/vocal_spectrogram.cpp
    jni/analysis_jni.cpp
    jni/mel_jni.cpp
    jni/vocal_jni.cpp
)

target_include_directories(bitchord_analysis PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${BITCHORD_NATIVE_DIR}
)

# -ffast-math is deliberately NOT enabled: the analyzer's log/mel-adjacent
# arithmetic (see audio_analysis.cpp) is tuned against exact IEEE behaviour.
target_compile_options(bitchord_analysis PRIVATE -O3 -fvisibility=hidden)

find_library(log-lib log)
target_link_libraries(bitchord_analysis ${log-lib})

# 16KB page-size support (Android 15+ 16KB kernels, Play requirement): align
# LOAD segments to 16KB. A superset of 4KB alignment, so 4KB devices are
# unaffected. APK-side alignment of uncompressed .so files is handled by AGP.
target_link_options(bitchord_analysis PRIVATE "-Wl,-z,max-page-size=16384")

# Automix's loop mix: two Signalsmith Stretch decks looping whole bars on a
# ramping tempo, rendered inside the outgoing player's audio sink. Signalsmith
# Stretch and its FFT (signalsmith-linear) are MIT and header-only, vendored
# under native/third_party. Fast-math is safe here (nothing in it depends on
# exact IEEE behaviour) and lets the STFT's complex arithmetic vectorise.
add_library(bitchord_loopmix SHARED
    ${BITCHORD_NATIVE_DIR}/loopmix/loop_mix_renderer.cpp
    jni/loopmix_jni.cpp
)
target_include_directories(bitchord_loopmix PRIVATE
    ${BITCHORD_NATIVE_DIR}
    ${BITCHORD_NATIVE_DIR}/third_party
)
target_compile_options(bitchord_loopmix PRIVATE -O3 -ffast-math -fvisibility=hidden -Wno-unused-parameter)
target_link_options(bitchord_loopmix PRIVATE "-Wl,-z,max-page-size=16384")

# DSD playback (DSF/DFF). The decimator turns 1-bit DSD into float PCM for
# DsdExtractor; DST is the lossless compression SACD rips use inside DFF.
# DST is LGPL (see dsd/dst/NOTICE.md), so it is a library of its own that can
# be rebuilt or swapped without touching the rest.
add_library(bitchord_dsd SHARED dsd/dsd_decimator.cpp)
target_compile_options(bitchord_dsd PRIVATE -O3 -fno-fast-math -ffp-contract=off -fvisibility=hidden)
target_link_options(bitchord_dsd PRIVATE "-Wl,-z,max-page-size=16384")

add_library(bitchord_dst SHARED dsd/dst/decoder.c dsd/dst/jni.cpp)
target_compile_options(bitchord_dst PRIVATE -O3 -fno-strict-aliasing -fvisibility=hidden)
target_link_options(bitchord_dst PRIVATE "-Wl,-z,max-page-size=16384")

# Bit-perfect output (see playback/audio/bitperfect). AAudio exclusive streams
# through Oboe, built from a pinned release with its format/rate conversion
# compiled out: a stream that needed converting is one this library rejects.
# Oboe is Apache-2.0; exclusive_output.cpp is adapted from Mornye (LGPL-3.0).
include(FetchContent)
set(OBOE_DISABLE_CONVERSION ON CACHE BOOL "" FORCE)
FetchContent_Declare(oboe
    URL https://github.com/google/oboe/archive/refs/tags/1.11.0.tar.gz
    URL_HASH SHA256=0ccf2110640a2b4489f1c93a404f2978656b434d8c5dc9cb6258f9cc95c79b15)
FetchContent_MakeAvailable(oboe)

add_library(bitchord_exclusive SHARED bitperfect/exclusive_output.cpp)
target_compile_options(bitchord_exclusive PRIVATE -O2 -Wall -Wextra -fvisibility=hidden)
target_link_libraries(bitchord_exclusive PRIVATE oboe ${log-lib})
# Only JNI is public: Oboe is static, and its symbols must not keep otherwise
# unused code alive or be exported from this library.
target_link_options(bitchord_exclusive PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--gc-sections"
    "-Wl,--version-script,${CMAKE_CURRENT_SOURCE_DIR}/bitperfect/exports.map")
set_property(TARGET bitchord_exclusive APPEND PROPERTY LINK_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/bitperfect/exports.map")

# Direct USB output: a userspace USB Audio Class driver on libusb, so a DAC
# can be streamed to without Android's mixer on any Android version. libusb
# (LGPL-2.1) is built from its pinned release using the source list and
# config.h it ships for Android (android/jni/libusb.mk). The transport is
# adapted from Mornye's Rust driver (LGPL-3.0).
FetchContent_Declare(libusb
    URL https://github.com/libusb/libusb/releases/download/v1.0.27/libusb-1.0.27.tar.bz2
    URL_HASH SHA256=ffaa41d741a8a3bee244ac8e54a72ea05bf2879663c098c82fc5757853441575)
FetchContent_MakeAvailable(libusb)
add_library(bitchord_libusb STATIC
    ${libusb_SOURCE_DIR}/libusb/core.c
    ${libusb_SOURCE_DIR}/libusb/descriptor.c
    ${libusb_SOURCE_DIR}/libusb/hotplug.c
    ${libusb_SOURCE_DIR}/libusb/io.c
    ${libusb_SOURCE_DIR}/libusb/sync.c
    ${libusb_SOURCE_DIR}/libusb/strerror.c
    ${libusb_SOURCE_DIR}/libusb/os/linux_usbfs.c
    ${libusb_SOURCE_DIR}/libusb/os/events_posix.c
    ${libusb_SOURCE_DIR}/libusb/os/threads_posix.c
    ${libusb_SOURCE_DIR}/libusb/os/linux_netlink.c)
target_include_directories(bitchord_libusb
    PRIVATE ${libusb_SOURCE_DIR}/android ${libusb_SOURCE_DIR}/libusb/os
    PUBLIC ${libusb_SOURCE_DIR}/libusb)
target_compile_options(bitchord_libusb PRIVATE -O2 -fvisibility=hidden -pthread -w)
set_target_properties(bitchord_libusb PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(bitchord_usb SHARED bitperfect/usb_transport.cpp)
target_compile_options(bitchord_usb PRIVATE -O2 -Wall -Wextra -fvisibility=hidden)
target_link_libraries(bitchord_usb PRIVATE bitchord_libusb ${log-lib})
target_link_options(bitchord_usb PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--gc-sections"
    "-Wl,--version-script,${CMAKE_CURRENT_SOURCE_DIR}/bitperfect/exports.map")
set_property(TARGET bitchord_usb APPEND PROPERTY LINK_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/bitperfect/exports.map")
