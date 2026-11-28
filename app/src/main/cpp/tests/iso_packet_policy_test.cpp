// These checks are the test: keep them live in an NDEBUG (release) build too.
#undef NDEBUG
#include "bitperfect/iso_packet_policy.h"

#include <cassert>
#include <cstddef>
#include <iostream>

using bitchord::usb::IsoPacketView;
using bitchord::usb::packetCompleted;
using bitchord::usb::packetMusicBytes;
using bitchord::usb::packetShort;
using bitchord::usb::packetUnsentLength;
using bitchord::usb::packetUnsentOffset;

// Sums packetMusicBytes over one transfer, the way the transport does.
static std::size_t transferMusicBytes(const IsoPacketView *packets, int count, std::size_t musicBytes) {
    std::size_t start = 0;
    std::size_t total = 0;
    for (int i = 0; i < count; ++i) {
        total += packetMusicBytes(packets[i], start, musicBytes);
        start += packets[i].length;
    }
    return total;
}

int main() {
    assert(packetCompleted({true, true, 192, 192}));
    assert(packetCompleted({true, true, 192, 96}));   // short OUT is legal
    assert(packetCompleted({true, true, 192, 0}));    // zero-length is legal
    assert(packetCompleted({true, false, 192, 96}));  // short IN is legal
    assert(!packetCompleted({false, true, 192, 96}));
    assert(!packetCompleted({true, true, 96, 192}));

    assert(packetShort({true, true, 192, 96}));
    assert(!packetShort({true, true, 192, 192}));
    assert(!packetShort({true, false, 192, 96}));
    assert(packetUnsentLength({true, true, 192, 96}) == 96);
    assert(packetUnsentOffset({true, true, 192, 96}, 384) == 480);

    // A full transfer of music counts every byte.
    const IsoPacketView full[] = {{true, true, 192, 192}, {true, true, 192, 192}};
    assert(transferMusicBytes(full, 2, 384) == 384);

    // Silence padding after the music is accepted by the device but is not
    // played music: only the 96 queued bytes count.
    assert(transferMusicBytes(full, 2, 96) == 96);

    // An all-silence transfer (queue ran dry) adds nothing to the clock.
    assert(transferMusicBytes(full, 2, 0) == 0);

    // A short packet inside the music drops the bytes the device refused.
    const IsoPacketView shortFirst[] = {{true, true, 192, 96}, {true, true, 192, 192}};
    assert(transferMusicBytes(shortFirst, 2, 384) == 96 + 192);

    // A short packet in the padding region costs no music.
    const IsoPacketView shortPadding[] = {{true, true, 192, 192}, {true, true, 192, 0}};
    assert(transferMusicBytes(shortPadding, 2, 192) == 192);

    // Music ending mid-packet: only the music part of that packet counts.
    assert(transferMusicBytes(full, 2, 288) == 288);

    std::cout << "iso packet policy tests passed\n";
    return 0;
}
