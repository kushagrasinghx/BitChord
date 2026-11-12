/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * rust_backend/crates/mobile/src/usb_audio/descriptors.rs,
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect.usb

/**
 * USB Audio Class descriptor parsing and format selection for the direct USB
 * driver: which alternate setting, endpoint and clock carry a stream at
 * exactly its own rate and precision.
 *
 * Only UAC1/UAC2 Type I mono or stereo output is accepted. Ambiguous clock
 * selectors, implicit feedback and multiple configurations are rejected
 * rather than guessed at — a wrong guess is a DAC playing at the wrong rate.
 * The descriptors come from the device, so every length is bounds-checked.
 */
internal object UsbDescriptors {

    class MalformedDescriptorException(message: String) : Exception(message)

    data class Endpoint(
        val address: Int = 0,
        val attributes: Int = 0,
        val maxPacket: Int = 0,
        val interval: Int = 0,
        val syncAddress: Int = 0,
        val rateControl: Boolean = false,
    )

    data class Alternate(
        val control: Int = 0,
        val interface_: Int = 0,
        val alternate: Int = 0,
        val uac2: Boolean = false,
        val terminal: Int = 0,
        val clock: Int = 0,
        val channels: Int = 0,
        val bits: Int = 0,
        val subslot: Int = 0,
        val pcm: Boolean = false,
        val rates: List<Pair<Int, Int>> = emptyList(),
        val endpoints: List<Endpoint> = emptyList(),
    )

    data class Device(
        val vendor: Int = 0,
        val product: Int = 0,
        val revision: Int = 0,
        val alternates: List<Alternate> = emptyList(),
    )

    enum class Encoding(val code: Int) {
        PCM(0),
        DOP(1),
        DSD_BE(2),
        DSD_LE(3),
    }

    /** What actually crosses the wire: the rate the endpoint runs at, and its sample container. */
    data class OutputFormat(
        val sampleRate: Int,
        val channels: Int,
        val bits: Int,
        val subslot: Int,
        val encoding: Encoding,
    )

    /**
     * Everything the native transport needs to set up one candidate: which
     * interfaces to claim, which alternate and clock to select, and which
     * endpoints to stream on.
     */
    data class Plan(val alternate: Alternate, val format: OutputFormat, val data: Endpoint, val feedback: Endpoint?)

    private fun u16le(b: ByteArray, at: Int) = (b[at].toInt() and 255) or ((b[at + 1].toInt() and 255) shl 8)
    private fun u24le(b: ByteArray, at: Int) = u16le(b, at) or ((b[at + 2].toInt() and 255) shl 16)
    private fun u32le(b: ByteArray, at: Int) = u16le(b, at) or (u16le(b, at + 2) shl 16)

    fun parse(bytes: ByteArray): Device {
        var vendor = 0
        var product = 0
        var revision = 0
        val alternates = mutableListOf<Alternate>()
        var current: Alternate? = null
        var control = 0
        var isControl = false
        val clocks = HashMap<Pair<Int, Int>, Int>()
        var offset = 0
        var configurations = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 255
            if (length < 2 || offset + length > bytes.size) throw MalformedDescriptorException("malformed USB descriptor")
            val d = bytes.copyOfRange(offset, offset + length)
            offset += length
            fun u(i: Int) = d[i].toInt() and 255
            when (u(1)) {
                2 -> {
                    configurations++
                    if (configurations > 1) {
                        throw MalformedDescriptorException("Multiple USB configurations are not supported")
                    }
                }
                1 -> if (length >= 18) {
                    vendor = u16le(d, 8)
                    product = u16le(d, 10)
                    revision = u16le(d, 12)
                }
                4 -> if (length >= 9) {
                    current?.let(alternates::add)
                    current = null
                    isControl = u(5) == 1 && u(6) == 1 && u(7) == 0x20
                    if (u(5) == 1 && u(6) == 1) control = u(2)
                    if (u(5) == 1 && u(6) == 2 && u(3) > 0 && (u(7) == 0 || u(7) == 0x20)) {
                        current = Alternate(control = control, interface_ = u(2), alternate = u(3), uac2 = u(7) == 0x20)
                    }
                }
                0x24 -> if (isControl && length >= 9 && u(2) == 2) {
                    // UAC2 input terminal: which clock source drives it.
                    clocks[control to u(3)] = u(7)
                } else {
                    current = current?.let { a -> classSpecific(a, d, length) }
                }
                5 -> if (length >= 7) {
                    current = current?.let { a ->
                        val packet = u16le(d, 4)
                        a.copy(
                            endpoints = a.endpoints + Endpoint(
                                address = u(2),
                                attributes = u(3),
                                maxPacket = (packet and 0x7ff) * (1 + ((packet shr 11) and 3)),
                                interval = u(6),
                                syncAddress = if (length > 8) u(8) else 0,
                            ),
                        )
                    }
                }
                0x25 -> if (length >= 4 && u(2) == 1) {
                    current = current?.let { a ->
                        val last = a.endpoints.lastOrNull()
                        if (a.uac2 || last == null) {
                            a
                        } else {
                            a.copy(endpoints = a.endpoints.dropLast(1) + last.copy(rateControl = u(3) and 1 != 0))
                        }
                    }
                }
            }
        }
        current?.let(alternates::add)
        return Device(
            vendor = vendor,
            product = product,
            revision = revision,
            alternates = alternates.map { it.copy(clock = clocks[it.control to it.terminal] ?: 0) },
        )
    }

    /** AS_GENERAL and FORMAT_TYPE, the two class-specific descriptors that describe a stream. */
    private fun classSpecific(a: Alternate, d: ByteArray, length: Int): Alternate {
        fun u(i: Int) = d[i].toInt() and 255
        var result = a
        if (length >= 7 && u(2) == 1) {
            result = result.copy(terminal = u(3))
            if (result.uac2 && length >= 16 && u(5) == 1) {
                result = result.copy(pcm = u32le(d, 6) and 1 != 0, channels = u(10))
            } else if (!result.uac2) {
                result = result.copy(pcm = u16le(d, 5) == 1)
            }
        }
        if (length >= 6 && u(2) == 2 && u(3) == 1) {
            if (result.uac2) {
                result = result.copy(subslot = u(4), bits = u(5))
            } else if (length >= 8) {
                val count = u(7)
                val rates = if (count == 0 && length >= 14) {
                    listOf(u24le(d, 8) to u24le(d, 11))
                } else if (length >= 8 + count * 3) {
                    (0 until count).map { val rate = u24le(d, 8 + it * 3); rate to rate }
                } else {
                    emptyList()
                }
                result = result.copy(channels = u(4), subslot = u(5), bits = u(6), rates = result.rates + rates)
            }
        }
        return result
    }

    /**
     * A deliberately small exact-match registry based on documented USB
     * transport identities in Linux sound/usb/quirks.c, not a product-name
     * heuristic.
     */
    private fun nativeEncoding(device: Device, a: Alternate): Encoding? {
        if (!a.uac2 || a.subslot != 4) return null
        return when {
            device.vendor == 0x16d0 && device.product == 0x071a && a.alternate == 2 && device.revision == 0x0199 ->
                Encoding.DSD_LE
            device.vendor == 0x16d0 && device.product == 0x071a && a.alternate == 2 &&
                (device.revision == 0x019b || device.revision == 0x0203) -> Encoding.DSD_BE
            (device.vendor == 0x2772 && device.product == 0x0230 && a.alternate == 2) ||
                (device.vendor == 0x20b1 && device.product == 0x3089 && a.alternate == 2) -> Encoding.DSD_BE
            else -> null
        }
    }

    fun formats(
        device: Device,
        rate: Int,
        channels: Int,
        bits: Int,
        dsd: Boolean,
        dop: Boolean,
    ): List<Pair<Alternate, OutputFormat>> {
        val result = mutableListOf<Pair<Alternate, OutputFormat>>()
        for (a in device.alternates) {
            if (a.channels != channels || channels !in 1..2 || a.subslot !in 2..4) continue
            val native = nativeEncoding(device, a)
            val (wireRate, encoding, precision) = if (dsd) {
                when {
                    native != null -> Triple(rate / 32, native, 1)
                    dop && a.pcm && a.bits >= 24 && a.subslot >= 3 -> Triple(rate / 16, Encoding.DOP, 24)
                    else -> continue
                }
            } else {
                if (!a.pcm || native != null || bits !in listOf(16, 24, 32) || a.bits < bits) continue
                Triple(rate, Encoding.PCM, a.bits)
            }
            if (wireRate == 0 || a.bits > a.subslot * 8 || (a.uac2 && a.clock == 0)) continue
            if (!a.uac2 && a.rates.none { (min, max) -> wireRate in min..max }) continue
            result += a to OutputFormat(wireRate, channels, precision, a.subslot, encoding)
        }
        return result.sortedWith(compareBy({ if (it.second.encoding.name.startsWith("DSD")) 0 else 1 }, { it.second.subslot }))
    }

    /**
     * The endpoint checks that need no device speed: an isochronous OUT data
     * endpoint, an explicit feedback endpoint when the data endpoint is
     * asynchronous, and a UAC1 clock that is either fixed at this rate or
     * settable. The native side checks bandwidth, which depends on speed.
     */
    fun plan(alternate: Alternate, format: OutputFormat): Plan? {
        val data = alternate.endpoints.firstOrNull {
            it.address and 0x80 == 0 && it.attributes and 3 == 1 && it.attributes and 0x30 == 0
        } ?: return null
        if (data.interval !in 1..4 || data.maxPacket == 0 || data.maxPacket > 3072) return null
        val feedback = alternate.endpoints.firstOrNull {
            it.address and 0x80 != 0 && it.attributes and 3 == 1 &&
                (it.address == data.syncAddress || it.attributes and 0x30 == 0x10)
        }
        // Implicit feedback means following the DAC's capture stream: not supported.
        if ((data.attributes shr 2) and 3 == 1 && feedback == null) return null
        if (feedback != null && feedback.maxPacket !in 3..64) return null
        if (!alternate.uac2 && !data.rateControl && alternate.rates != listOf(format.sampleRate to format.sampleRate)) {
            return null
        }
        return Plan(alternate, format, data, feedback)
    }
}
