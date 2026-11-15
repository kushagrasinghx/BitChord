#include "bitperfect/iso_packet_policy.h"

#include <cassert>
#include <iostream>

using bitchord::usb::IsoPacketView;
using bitchord::usb::packetCompleted;
using bitchord::usb::packetShort;
using bitchord::usb::packetUnsentLength;
using bitchord::usb::packetUnsentOffset;

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

    std::cout << "iso packet policy tests passed\n";
    return 0;
}
