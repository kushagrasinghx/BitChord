// Host tests for usb_stream.h, ported from Mornye's Rust tests
// (descriptors.rs PacketClock, framing.rs). Build with any C++17 compiler:
//   g++ -std=c++17 -I.. usb_stream_test.cpp -o usb_stream_test && ./usb_stream_test
#include "usb_stream.h"

#include <cstdio>
#include <cstdlib>
#include <vector>

using namespace usbaudio;

static int failures = 0;
#define EXPECT(cond)                                                       \
    do {                                                                   \
        if (!(cond)) {                                                     \
            std::printf("FAILED %s:%d: %s\n", __FILE__, __LINE__, #cond); \
            failures++;                                                    \
        }                                                                  \
    } while (0)

static Format format(Encoding encoding, int subslot) {
    Format f;
    f.sampleRate = 176400;
    f.channels = 2;
    f.bits = 24;
    f.subslot = subslot;
    f.encoding = encoding;
    return f;
}

static void fractionalRatesDoNotDrift() {
    const struct { uint32_t rate, interval; int packets; } cases[] = {
        {44100, 125, 8000}, {88200, 1000, 1000}, {176400, 125, 8000}};
    for (auto c : cases) {
        PacketClock clock(c.rate, c.interval);
        size_t sum = 0;
        for (int i = 0; i < c.packets; ++i) sum += clock.next();
        EXPECT(sum == c.rate);
    }
}

static void invalidFeedbackCannotOverrunEndpoint() {
    PacketClock clock(48000, 125);
    uint8_t high[4] = {255, 255, 255, 255};
    uint8_t zero[4] = {0, 0, 0, 0};
    EXPECT(!clock.feedback(high, 4, true, 125));
    EXPECT(!clock.feedback(zero, 4, true, 125));
    uint32_t six = 6u << 16;
    EXPECT(clock.feedback(reinterpret_cast<uint8_t *>(&six), 4, true, 125));
    EXPECT(clock.next() == 6);
}

static void dopMarkersAlternateAcrossTransfersAndUnderruns() {
    uint8_t marker = 5;
    std::vector<uint8_t> first = {1, 2, 0, 3, 4, 0};
    finishTransfer(first.data(), first.size(), 6, format(Encoding::Dop, 3), marker);
    EXPECT((first == std::vector<uint8_t>{1, 2, 5, 3, 4, 5}));
    std::vector<uint8_t> second(12, 0);
    finishTransfer(second.data(), second.size(), 0, format(Encoding::Dop, 3), marker);
    EXPECT((second == std::vector<uint8_t>{0x69, 0x69, 0xfa, 0x69, 0x69, 0xfa, 0x69, 0x69, 5, 0x69, 0x69, 5}));
    EXPECT(marker == 0xfa);
}

static void dop32PaddingAndChannelMarkersMatch() {
    uint8_t marker = 5;
    std::vector<uint8_t> data = {0, 1, 2, 0, 0, 3, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    finishTransfer(data.data(), data.size(), 8, format(Encoding::Dop, 4), marker);
    EXPECT((data == std::vector<uint8_t>{0, 1, 2, 5, 0, 3, 4, 5, 0, 0x69, 0x69, 0xfa, 0, 0x69, 0x69, 0xfa}));
}

static void nativeDsdAndPcmKeepOriginalDataAndUseDifferentSilence() {
    const struct { Encoding encoding; uint8_t silence; } cases[] = {
        {Encoding::Pcm, 0}, {Encoding::DsdBe, 0x69}, {Encoding::DsdLe, 0x69}};
    for (auto c : cases) {
        uint8_t marker = 5;
        std::vector<uint8_t> bytes = {1, 2, 3, 4, 5, 6, 7, 8, 255, 255, 255, 255, 255, 255, 255, 255};
        finishTransfer(bytes.data(), bytes.size(), 8, format(c.encoding, 4), marker);
        for (int i = 0; i < 8; ++i) EXPECT(bytes[i] == i + 1);
        for (int i = 8; i < 16; ++i) EXPECT(bytes[i] == c.silence);
    }
}

int main() {
    fractionalRatesDoNotDrift();
    invalidFeedbackCannotOverrunEndpoint();
    dopMarkersAlternateAcrossTransfersAndUnderruns();
    dop32PaddingAndChannelMarkersMatch();
    nativeDsdAndPcmKeepOriginalDataAndUseDifferentSilence();
    std::printf(failures ? "%d failure(s)\n" : "all passed\n", failures);
    return failures ? EXIT_FAILURE : EXIT_SUCCESS;
}
