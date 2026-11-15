#pragma once

#include <cstddef>

namespace bitchord::usb {

// One libusb isochronous packet reduced to the facts the transport needs.
// Keeping this header free of libusb makes the policy testable on the host.
struct IsoPacketView {
    bool completed;
    bool outbound;
    unsigned length;
    unsigned actual;
};

inline bool packetCompleted(const IsoPacketView &packet) {
    return packet.completed && packet.actual <= packet.length;
}

inline bool packetShort(const IsoPacketView &packet) {
    return packet.outbound && packet.actual < packet.length;
}

// Offset in the transfer buffer where the bytes the device did not receive
// begin. The caller zeroes this range so a short completion never replays
// stale audio that happened to occupy the same buffer.
inline std::size_t packetUnsentOffset(const IsoPacketView &packet,
                                      std::size_t packetStart) {
    return packetStart + packet.actual;
}

inline std::size_t packetUnsentLength(const IsoPacketView &packet) {
    return packet.length > packet.actual ? packet.length - packet.actual : 0;
}

}  // namespace bitchord::usb
