# Native analysis front end for Smart Fade.
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
project(bitchord_analysis LANGUAGES CXX)

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
