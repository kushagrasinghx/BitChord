// SPDX-License-Identifier: LGPL-2.1-or-later
#include <jni.h>
#include <array>
#include <cstdint>
#include "decoder.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_music_bitchord_playback_dsd_DstDecoder_create(JNIEnv*, jobject, jint channels) {
    dst_decoder_t* decoder = nullptr;
    if (dst_decoder_init(&decoder, channels, 2822400) != 0) return 0;
    return reinterpret_cast<jlong>(decoder);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_music_bitchord_playback_dsd_DstDecoder_decode(JNIEnv* env, jobject, jlong handle,
        jbyteArray input, jbyteArray output) {
    if (!handle || !input || !output) return -1;
    const int size = env->GetArrayLength(input), capacity = env->GetArrayLength(output);
    if (size < 2 || size > 1048576 || capacity < 4704 || capacity > 9408) return -1;
    jbyte* data = env->GetByteArrayElements(input, nullptr);
    if (!data) return -1;
    std::array<uint8_t, 9408> buffer{};
    int count = capacity;
    const int status = dst_decoder_decode(reinterpret_cast<dst_decoder_t*>(handle),
        reinterpret_cast<uint8_t*>(data), size, buffer.data(), &count);
    env->ReleaseByteArrayElements(input, data, JNI_ABORT);
    if (status != 0) return status;
    if (count < 0 || count > capacity) return -1;
    env->SetByteArrayRegion(output, 0, count, reinterpret_cast<jbyte*>(buffer.data()));
    return count;
}

extern "C" JNIEXPORT void JNICALL
Java_com_music_bitchord_playback_dsd_DstDecoder_destroy(JNIEnv*, jobject, jlong handle) {
    dst_decoder_close(reinterpret_cast<dst_decoder_t*>(handle));
}
