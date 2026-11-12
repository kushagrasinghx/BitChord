package com.my.kizzy.gateway.entities.op

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class OpCodeSerializer : KSerializer<OpCode> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("OpCode", PrimitiveKind.INT)

    /**
     * Maps an unrecognised opcode to [OpCode.UNKNOWN] rather than throwing.
     *
     * Discord's user gateway sends opcodes this app has no interest in and the
     * set grows over time. Refusing to decode one threw out of the frame pump,
     * which killed the connection — so an opcode added after a release could
     * stop rich presence working until the app was updated.
     */
    override fun deserialize(decoder: Decoder): OpCode {
        val opCode = decoder.decodeInt()
        return OpCode.entries.firstOrNull { it.value == opCode } ?: OpCode.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: OpCode) {
        encoder.encodeInt(value.value)
    }
}
