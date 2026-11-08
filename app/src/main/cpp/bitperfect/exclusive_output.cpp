/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * android/app/src/main/cpp/native_audio.cpp (ExclusiveOutput),
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

// Exact-rate PCM through an AAudio exclusive stream (Oboe), for players with
// a built-in DAC. Exclusive is only a request: Android may grant a shared
// stream instead, and a shared stream is mixed. The stream is accepted only
// when the API, sharing mode, rate, channel count and format all came back
// exactly as asked; otherwise it is closed and the caller uses normal output.

#include <jni.h>
#include <oboe/Oboe.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <unordered_map>
#include <vector>

namespace {
void require(bool condition, const char *message) {
    if (!condition) throw std::runtime_error(message);
}

void report(JNIEnv *env, const std::exception &error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}

// Handles never expose raw pointers. A stale or double close cannot reach a
// freed stream; each JNI call holds ownership for its whole duration.
template <class T> class Handles {
    std::mutex mutex;
    std::unordered_map<jlong, std::shared_ptr<T>> values;
    jlong next = 1;

public:
    jlong add(std::shared_ptr<T> value) {
        std::lock_guard<std::mutex> lock(mutex);
        const auto id = next++;
        values.emplace(id, std::move(value));
        return id;
    }
    std::shared_ptr<T> get(jlong id) {
        std::lock_guard<std::mutex> lock(mutex);
        auto it = values.find(id);
        require(it != values.end(), "Native audio handle closed");
        return it->second;
    }
    void remove(jlong id) {
        std::shared_ptr<T> old;
        {
            std::lock_guard<std::mutex> lock(mutex);
            auto it = values.find(id);
            if (it != values.end()) {
                old = std::move(it->second);
                values.erase(it);
            }
        }
    }
};

class ExclusiveOutput final : public oboe::AudioStreamDataCallback {
    std::shared_ptr<oboe::AudioStream> stream;
    // Single producer (the playback thread), single consumer (the audio callback).
    std::vector<uint8_t> ring;
    std::atomic<uint64_t> read{0}, written{0}, musicFrames{0};
    std::atomic<int64_t> lastMusicEnd{0};
    int64_t callbackFrames = 0;
    int frameBytes = 0;
    bool floatOutput = false;
    int64_t frameBase = 0;

public:
    int bits = 0;
    int deviceId() const { return stream->getDeviceId(); }
    int capacityFrames() const { return static_cast<int>(ring.size() / frameBytes); }

    ExclusiveOutput(int rate, int channels, int precision, int device) {
        require(rate >= 8000 && rate <= 768000 && channels >= 1 && channels <= 2,
                "Unsupported hi-res format");
        require(precision == 16 || precision == 24 || precision == 32, "Unsupported PCM precision");
        std::vector<oboe::AudioFormat> formats;
        if (precision == 16) formats.push_back(oboe::AudioFormat::I16);
        if (precision <= 24) formats.push_back(oboe::AudioFormat::I24);
        formats.push_back(oboe::AudioFormat::I32);
        if (precision <= 24) formats.push_back(oboe::AudioFormat::Float);
        std::ostringstream attempts;
        attempts << "requested=" << rate << "Hz/" << channels << "ch/" << precision
                 << "bit, device=" << device;
        for (auto format : formats) {
            oboe::AudioStreamBuilder builder;
            builder.setDirection(oboe::Direction::Output)
                ->setAudioApi(oboe::AudioApi::AAudio)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setUsage(oboe::Usage::Media)
                ->setContentType(oboe::ContentType::Music)
                ->setSampleRate(rate)
                ->setChannelCount(channels)
                ->setFormat(format)
                ->setDeviceId(device)
                ->setDataCallback(this)
                ->setChannelConversionAllowed(false)
                ->setFormatConversionAllowed(false)
                ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::None);
            const auto result = builder.openStream(stream);
            attempts << "; format=" << oboe::convertToText(format);
            if (result != oboe::Result::OK) {
                attempts << " open=" << oboe::convertToText(result);
                continue;
            }
            // Exclusive is a request: Android can silently return Shared. Reject
            // that stream instead of showing a misleading exclusive badge.
            if (stream->getAudioApi() == oboe::AudioApi::AAudio &&
                stream->getSharingMode() == oboe::SharingMode::Exclusive &&
                stream->getSampleRate() == rate && stream->getChannelCount() == channels &&
                stream->getFormat() == format && (device == 0 || stream->getDeviceId() == device)) {
                floatOutput = format == oboe::AudioFormat::Float;
                bits = format == oboe::AudioFormat::I16 ? 16 : format == oboe::AudioFormat::I24 ? 24 : 32;
                frameBytes = channels * (bits / 8);
                ring.resize(static_cast<size_t>(rate / 4) * frameBytes);
                return;
            }
            attempts << " actual=" << oboe::convertToText(stream->getSharingMode()) << "/"
                     << stream->getSampleRate() << "Hz/" << stream->getChannelCount() << "ch/"
                     << oboe::convertToText(stream->getFormat())
                     << ", api=" << oboe::convertToText(stream->getAudioApi())
                     << ", device=" << stream->getDeviceId();
            stream->close();
            stream.reset();
        }
        throw std::runtime_error("Exact-rate AAudio exclusive output unavailable: " + attempts.str());
    }

    ~ExclusiveOutput() override {
        if (stream) stream->close();
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *, void *audio, int32_t frames) override {
        const auto r = read.load(std::memory_order_relaxed);
        const auto w = written.load(std::memory_order_acquire);
        const size_t requested = static_cast<size_t>(frames) * frameBytes;
        const size_t available = std::min<uint64_t>(requested, w - r);
        const size_t start = r % ring.size();
        const size_t first = std::min(available, ring.size() - start);
        auto *out = static_cast<uint8_t *>(audio);
        std::memcpy(out, ring.data() + start, first);
        std::memcpy(out + first, ring.data(), available - first);
        std::memset(out + available, 0, requested - available);
        read.store(r + available, std::memory_order_release);
        musicFrames.fetch_add(available / frameBytes, std::memory_order_relaxed);
        if (available > 0) {
            lastMusicEnd.store(frameBase + callbackFrames + static_cast<int64_t>(available / frameBytes),
                               std::memory_order_release);
        }
        callbackFrames += frames;
        return oboe::DataCallbackResult::Continue;
    }

    void check() {
        require(stream && stream->getState() != oboe::StreamState::Disconnected &&
                stream->getState() != oboe::StreamState::Closed,
                "AAudio output disconnected");
    }

    int write(const uint8_t *data, size_t size) {
        check();
        require(size % frameBytes == 0, "Unaligned PCM buffer");
        auto w = written.load(std::memory_order_relaxed);
        auto r = read.load(std::memory_order_acquire);
        size = std::min<uint64_t>(size, ring.size() - (w - r)) / frameBytes * frameBytes;
        for (size_t offset = 0; offset < size;) {
            const size_t index = (w + offset) % ring.size();
            const size_t count = std::min(size - offset, ring.size() - index);
            if (floatOutput) {
                // Integer samples of 24 bits or fewer, padded to 32, have an exact
                // float representation. This path is never opened for 32-bit sources.
                for (size_t sample = 0; sample < count; sample += 4) {
                    int32_t integer;
                    std::memcpy(&integer, data + offset + sample, 4);
                    const float value = static_cast<float>(integer) * (1.0f / 2147483648.0f);
                    std::memcpy(ring.data() + index + sample, &value, 4);
                }
            } else {
                std::memcpy(ring.data() + index, data + offset, count);
            }
            offset += count;
        }
        written.store(w + size, std::memory_order_release);
        return static_cast<int>(size);
    }

    void start() {
        check();
        require(stream->requestStart() == oboe::Result::OK, "AAudio start failed");
    }

    // Unlike flush, keeps the ring: the stream resumes where it stopped.
    void pause() {
        check();
        const auto state = stream->getState();
        if (state == oboe::StreamState::Started || state == oboe::StreamState::Starting) {
            require(stream->pause(2000000000LL) == oboe::Result::OK, "AAudio pause failed");
        }
    }

    void flush() {
        check();
        // Wait for the callback to stop before resetting its ring and counters.
        const auto state = stream->getState();
        if (state != oboe::StreamState::Open && state != oboe::StreamState::Flushed) {
            if (state != oboe::StreamState::Paused) {
                require(stream->pause(2000000000LL) == oboe::Result::OK, "AAudio pause failed");
            }
            require(stream->flush(2000000000LL) == oboe::Result::OK, "AAudio flush failed");
        }
        read.store(0);
        written.store(0);
        musicFrames.store(0);
        frameBase = stream->getFramesWritten();
        callbackFrames = 0;
        lastMusicEnd.store(frameBase);
    }

    // Music frames the hardware has presented, excluding the silence the
    // callback inserts when the ring runs dry.
    int64_t frames() {
        check();
        int64_t presented = 0, nanos = 0;
        auto music = static_cast<int64_t>(musicFrames.load(std::memory_order_relaxed));
        if (stream->getTimestamp(CLOCK_MONOTONIC, &presented, &nanos) == oboe::Result::OK) {
            const auto pending = std::max<int64_t>(0, lastMusicEnd.load(std::memory_order_acquire) - presented);
            return std::max<int64_t>(0, music - pending);
        }
        // A timestamp may not exist until the first hardware burst completes.
        return 0;
    }
};

Handles<ExclusiveOutput> outputs;
} // namespace

#define JNI_EXCLUSIVE(name) Java_com_music_bitchord_playback_audio_bitperfect_ExclusiveNative_##name

extern "C" {
JNIEXPORT jlong JNICALL JNI_EXCLUSIVE(open)(JNIEnv *env, jobject, jint rate, jint channels, jint bits,
                                            jint device) {
    try {
        return outputs.add(std::make_shared<ExclusiveOutput>(rate, channels, bits, device));
    } catch (const std::exception &e) {
        report(env, e);
        return 0;
    }
}
JNIEXPORT jint JNICALL JNI_EXCLUSIVE(bits)(JNIEnv *env, jobject, jlong handle) {
    try { return outputs.get(handle)->bits; } catch (const std::exception &e) { report(env, e); return 0; }
}
JNIEXPORT jint JNICALL JNI_EXCLUSIVE(device)(JNIEnv *env, jobject, jlong handle) {
    try { return outputs.get(handle)->deviceId(); } catch (const std::exception &e) { report(env, e); return 0; }
}
JNIEXPORT jint JNICALL JNI_EXCLUSIVE(capacity)(JNIEnv *env, jobject, jlong handle) {
    try { return outputs.get(handle)->capacityFrames(); } catch (const std::exception &e) { report(env, e); return 0; }
}
JNIEXPORT jint JNICALL JNI_EXCLUSIVE(write)(JNIEnv *env, jobject, jlong handle, jobject buffer, jint offset,
                                            jint count) {
    try {
        auto *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
        auto size = env->GetDirectBufferCapacity(buffer);
        require(data && offset >= 0 && count >= 0 && count <= 262144 && offset <= size && count <= size - offset,
                "Invalid PCM buffer");
        return outputs.get(handle)->write(data + offset, count);
    } catch (const std::exception &e) {
        report(env, e);
        return 0;
    }
}
JNIEXPORT void JNICALL JNI_EXCLUSIVE(start)(JNIEnv *env, jobject, jlong handle) {
    try { outputs.get(handle)->start(); } catch (const std::exception &e) { report(env, e); }
}
JNIEXPORT void JNICALL JNI_EXCLUSIVE(pause)(JNIEnv *env, jobject, jlong handle) {
    try { outputs.get(handle)->pause(); } catch (const std::exception &e) { report(env, e); }
}
JNIEXPORT void JNICALL JNI_EXCLUSIVE(flush)(JNIEnv *env, jobject, jlong handle) {
    try { outputs.get(handle)->flush(); } catch (const std::exception &e) { report(env, e); }
}
JNIEXPORT jlong JNICALL JNI_EXCLUSIVE(frames)(JNIEnv *env, jobject, jlong handle) {
    try { return outputs.get(handle)->frames(); } catch (const std::exception &e) { report(env, e); return 0; }
}
JNIEXPORT void JNICALL JNI_EXCLUSIVE(close)(JNIEnv *, jobject, jlong handle) { outputs.remove(handle); }
}
