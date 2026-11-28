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

// Bytes of music this packet delivered. A transfer carries `musicBytes` of
// queued audio first and silence padding after it (see finishTransfer), and
// only the music counts as played: counting accepted padding would make the
// played-frame clock run on through an underrun or past the end of a stream.
// A short packet delivered only its first `actual` bytes.
inline std::size_t packetMusicBytes(const IsoPacketView &packet,
                                    std::size_t packetStart,
                                    std::size_t musicBytes) {
    if (packetStart >= musicBytes) return 0;
    const std::size_t sent = packet.actual < packet.length ? packet.actual : packet.length;
    const std::size_t remaining = musicBytes - packetStart;
    return sent < remaining ? sent : remaining;
}

}  // namespace bitchord::usb
