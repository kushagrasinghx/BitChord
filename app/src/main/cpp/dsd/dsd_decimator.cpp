// DSD -> PCM decimation for DsdBlockDecoder.
//
// A 1-bit DSD stream is turned into 176.4 kHz float PCM by one long FIR
// low-pass (the taps come from Kotlin, DsdFilter) evaluated once per
// `decimation` input bits. The input arrives as whole bytes, so each tap group
// covers eight bits, and the sum over those eight is precomputed per nibble:
// two 16-entry lookups per group instead of eight multiply-adds.
//
// History is a ring of `groups` bytes per channel. Each slot carries how many
// of its eight bits are real (the high byte) so the very first and very last
// bytes of a track, which are partly padding, contribute only their valid
// bits; the fast nibble path runs only once every slot is full.

#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <new>
#include <vector>

namespace {
struct Decoder {
    int channels, decimation, groups, position = 0, full = 0;
    int64_t samples, source = 0, frame = 0;
    std::vector<double> coefficients;
    std::vector<float> lookup;
    std::vector<uint16_t> history;

    Decoder(int c, int d, int64_t s, std::vector<double> taps)
        : channels(c), decimation(d), groups(static_cast<int>(taps.size()) / 8), samples(s),
          coefficients(std::move(taps)), lookup(groups * 32), history(groups * c) {
        for (int g = 0; g < groups; ++g) for (int n = 0; n < 2; ++n) for (int v = 0; v < 16; ++v) {
            double sum = 0;
            for (int b = 0; b < 4; ++b) sum += coefficients[g * 8 + n * 4 + b] * ((v & (1 << b)) ? 1 : -1);
            lookup[g * 32 + n * 16 + v] = static_cast<float>(sum);
        }
    }

    double value(int group, int byte) const {
        return static_cast<double>(lookup[group * 32 + (byte & 15)]) + lookup[group * 32 + 16 + ((byte >> 4) & 15)];
    }

    bool push(const uint8_t* input, int valid, float* output) {
        if (history[position] >> 8 == 8) --full;
        if (valid == 8) ++full;
        for (int c = 0; c < channels; ++c) history[c * groups + position] =
            static_cast<uint16_t>((valid << 8) | (valid ? input[c] : 0));
        ++source;
        const bool ready = source * 8 >= frame * decimation + groups * 4;
        if (ready) {
            double left = 0, right = 0;
            int ring = position;
            if (full == groups) {
                for (int g = 0; g < groups; ++g) {
                    left += value(g, history[ring]);
                    if (channels == 2) right += value(g, history[groups + ring]);
                    ring = (ring - 1) & (groups - 1);
                }
                output[0] = static_cast<float>(left);
                if (channels == 2) output[1] = static_cast<float>(right);
            } else for (int c = 0; c < channels; ++c) {
                double sum = 0;
                ring = position;
                for (int g = 0; g < groups; ++g) {
                    const int packed = history[c * groups + ring], count = packed >> 8;
                    if (count == 8) sum += value(g, packed);
                    else for (int b = 8 - count; b < 8; ++b)
                        sum += coefficients[g * 8 + b] * ((packed & (1 << b)) ? 1 : -1);
                    ring = (ring - 1) & (groups - 1);
                }
                output[c] = static_cast<float>(sum);
            }
            ++frame;
        }
        position = (position + 1) & (groups - 1);
        return ready;
    }
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_music_bitchord_playback_dsd_DsdBlockDecoder_create(JNIEnv* env, jobject, jint channels,
        jint decimation, jlong samples, jdoubleArray coefficients) {
    if (!coefficients || channels < 1 || channels > 2 || samples <= 0 ||
        (decimation != 16 && decimation != 32 && decimation != 64 && decimation != 128 && decimation != 256)) return 0;
    const int taps = env->GetArrayLength(coefficients);
    if (taps != 32 * decimation) return 0;
    try {
        std::vector<double> values(taps);
        env->GetDoubleArrayRegion(coefficients, 0, taps, values.data());
        if (env->ExceptionCheck()) return 0;
        return reinterpret_cast<jlong>(new Decoder(channels, decimation, samples, std::move(values)));
    } catch (const std::bad_alloc&) { return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_music_bitchord_playback_dsd_DsdBlockDecoder_reset(JNIEnv*, jobject, jlong handle, jlong byte, jlong frame) {
    auto* d = reinterpret_cast<Decoder*>(handle);
    d->source = byte; d->frame = frame; d->position = 0; d->full = 0;
    std::fill(d->history.begin(), d->history.end(), 0);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_music_bitchord_playback_dsd_DsdBlockDecoder_decode(JNIEnv* env, jobject, jlong handle,
        jbyteArray bytes, jint size, jfloatArray output) {
    auto* d = reinterpret_cast<Decoder*>(handle);
    if (!d || !output || size < 0 || size > 8192 || size % d->channels ||
        (bytes && size > env->GetArrayLength(bytes)) || (!bytes && size)) return -1;
    const int64_t total = (d->samples + d->decimation - 1) / d->decimation;
    const int count = size / d->channels;
    if (d->frame >= total) return 0;
    if ((bytes && (d->source + count) > (d->samples + 7) / 8) || (!bytes && d->source * 8 < d->samples)) return -1;
    // No more input: flush the filter's tail with padding until the last
    // output frame the track's length calls for has been produced.
    const int64_t endSource = bytes ? d->source + count : (total * d->decimation + d->groups * 4 + 7) / 8;
    const int64_t lastFrame = (endSource * 8 - d->groups * 4) / d->decimation;
    const int64_t needed = std::max<int64_t>(0, std::min(total, lastFrame + 1) - d->frame);
    if (needed * d->channels > env->GetArrayLength(output)) return -1;
    uint8_t input[8192];
    if (bytes) env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte*>(input));
    if (env->ExceptionCheck()) return -1;
    float* result = env->GetFloatArrayElements(output, nullptr);
    if (!result) return -1;
    int produced = 0;
    for (int i = 0; d->source < endSource && d->frame < total; ++i) {
        int valid = bytes ? static_cast<int>(std::min<int64_t>(8, d->samples - d->source * 8)) : 0;
        if (d->push(bytes ? input + i * d->channels : nullptr, valid, result + produced * d->channels)) ++produced;
    }
    env->ReleaseFloatArrayElements(output, result, 0);
    return produced;
}

extern "C" JNIEXPORT void JNICALL
Java_com_music_bitchord_playback_dsd_DsdBlockDecoder_destroy(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Decoder*>(handle);
}
