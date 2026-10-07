package com.music.bitchord.ui.tv.auth

/**
 * Pure Kotlin QR Code Generator implementing ISO/IEC 18004.
 * Generates deterministic QR matrix for local URLs without external dependencies.
 */
object QrCodeGenerator {

    /**
     * Generates a 2D boolean array representing the QR code matrix (true = dark, false = light).
     */
    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        // Select smallest standard QR version that fits the payload with Error Correction Level M
        val version = when {
            bytes.size <= 14 -> 1
            bytes.size <= 26 -> 2
            bytes.size <= 42 -> 3
            bytes.size <= 62 -> 4
            bytes.size <= 84 -> 5
            bytes.size <= 106 -> 6
            else -> 7
        }

        val size = version * 4 + 17
        val matrix = Array(size) { BooleanArray(size) }
        val isReserved = Array(size) { BooleanArray(size) }

        // 1. Finder patterns
        fun placeFinder(top: Int, left: Int) {
            for (r in 0 until 7) {
                for (c in 0 until 7) {
                    val isBlack = r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4)
                    matrix[top + r][left + c] = isBlack
                    isReserved[top + r][left + c] = true
                }
            }
            // Separator around finder
            for (r in -1..7) {
                for (c in -1..7) {
                    val rr = top + r
                    val cc = left + c
                    if (rr in 0 until size && cc in 0 until size) {
                        isReserved[rr][cc] = true
                    }
                }
            }
        }

        placeFinder(0, 0)
        placeFinder(0, size - 7)
        placeFinder(size - 7, 0)

        // 2. Alignment patterns for Version >= 2
        if (version >= 2) {
            val alignPos = when (version) {
                2 -> intArrayOf(6, 18)
                3 -> intArrayOf(6, 22)
                4 -> intArrayOf(6, 26)
                5 -> intArrayOf(6, 30)
                6 -> intArrayOf(6, 34)
                else -> intArrayOf(6, 22, 38)
            }
            for (r in alignPos) {
                for (c in alignPos) {
                    if ((r == 6 && c == 6) || (r == 6 && c == alignPos.last()) || (r == alignPos.last() && c == 6)) {
                        continue // Skip finders
                    }
                    for (dr in -2..2) {
                        for (dc in -2..2) {
                            val isDark = dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0)
                            matrix[r + dr][c + dc] = isDark
                            isReserved[r + dr][c + dc] = true
                        }
                    }
                }
            }
        }

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            val isDark = i % 2 == 0
            if (!isReserved[6][i]) {
                matrix[6][i] = isDark
                isReserved[6][i] = true
            }
            if (!isReserved[i][6]) {
                matrix[i][6] = isDark
                isReserved[i][6] = true
            }
        }

        // 4. Dark module
        matrix[4 * version + 9][8] = true
        isReserved[4 * version + 9][8] = true

        // 5. Reserve format info areas
        for (i in 0 until 9) {
            isReserved[8][i] = true
            isReserved[i][8] = true
        }
        for (i in 0 until 8) {
            isReserved[8][size - 1 - i] = true
            isReserved[size - 1 - i][8] = true
        }

        // 6. Encode Data (Byte Mode = 0100)
        val dataCapacity = getDataCapacityBytes(version)
        val bitBuffer = mutableListOf<Int>()

        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bitBuffer.add((value ushr i) and 1)
            }
        }

        // Mode: Byte (0100)
        appendBits(0b0100, 4)
        // Length (8 bits for version 1-9)
        appendBits(bytes.size, 8)
        // Data bytes
        for (b in bytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        // Terminator
        val termLen = (dataCapacity * 8 - bitBuffer.size).coerceAtMost(4)
        if (termLen > 0) appendBits(0, termLen)
        // Byte align
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(0)
        }
        // Pad bytes
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.size < dataCapacity * 8) {
            appendBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        // Convert bitBuffer to byte array
        val dataBytes = ByteArray(dataCapacity)
        for (i in dataBytes.indices) {
            var b = 0
            for (bit in 0 until 8) {
                b = (b shl 1) or bitBuffer[i * 8 + bit]
            }
            dataBytes[i] = b.toByte()
        }

        // 7. Error Correction Codewords (Reed-Solomon)
        val ecCapacity = getEcCapacityBytes(version)
        val ecBytes = calculateReedSolomon(dataBytes, ecCapacity)

        val fullPayload = mutableListOf<Int>()
        for (b in dataBytes) fullPayload.add(b.toInt() and 0xFF)
        for (b in ecBytes) fullPayload.add(b.toInt() and 0xFF)

        // Convert full payload to bit stream
        val fullBits = mutableListOf<Int>()
        for (b in fullPayload) {
            for (i in 7 downTo 0) {
                fullBits.add((b ushr i) and 1)
            }
        }

        // 8. Place Data Bits in 2-column zig-zag
        var bitIndex = 0
        var right = size - 1
        var upward = true

        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing column

            val rows = if (upward) (size - 1 downTo 0).toList() else (0 until size).toList()
            for (r in rows) {
                for (colOffset in 0..1) {
                    val c = right - colOffset
                    if (!isReserved[r][c]) {
                        val bit = if (bitIndex < fullBits.size) fullBits[bitIndex++] else 0
                        // Mask Pattern 0: (row + col) % 2 == 0
                        val isMasked = (r + c) % 2 == 0
                        matrix[r][c] = if (isMasked) (bit xor 1) == 1 else bit == 1
                    }
                }
            }
            right -= 2
            upward = !upward
        }

        // 9. Format Information (Mask 0, EC Level M = 00)
        // Format bits for Level M, Mask 0 with BCH (15, 5): 0b101010000010010 XOR 0b101010000010010 -> 0x5412 XOR mask
        val formatBits = getFormatInfoBits(0b00, 0) // EC M (00), Mask 0
        for (i in 0..5) matrix[8][i] = (formatBits ushr (14 - i) and 1) == 1
        matrix[8][7] = (formatBits ushr 8 and 1) == 1
        matrix[8][8] = (formatBits ushr 7 and 1) == 1
        matrix[7][8] = (formatBits ushr 6 and 1) == 1
        for (i in 0..5) matrix[5 - i][8] = (formatBits ushr (5 - i) and 1) == 1

        for (i in 0..7) matrix[size - 1 - i][8] = (formatBits ushr (14 - i) and 1) == 1
        for (i in 0..7) matrix[8][size - 8 + i] = (formatBits ushr (7 - i) and 1) == 1

        return matrix
    }

    private fun getDataCapacityBytes(version: Int): Int = when (version) {
        1 -> 16
        2 -> 28
        3 -> 44
        4 -> 64
        5 -> 86
        6 -> 108
        else -> 124
    }

    private fun getEcCapacityBytes(version: Int): Int = when (version) {
        1 -> 10
        2 -> 16
        3 -> 26
        4 -> 36
        5 -> 48
        6 -> 64
        else -> 72
    }

    private fun getFormatInfoBits(ecLevel: Int, maskPattern: Int): Int {
        val data = (ecLevel shl 3) or maskPattern
        var rem = data shl 10
        for (i in 4 downTo 0) {
            if ((rem and (1 shl (i + 10))) != 0) {
                rem = rem xor (0x537 shl i)
            }
        }
        val full = (data shl 10) or rem
        return full xor 0x5412
    }

    // Reed-Solomon over GF(256) with generator 0x11D
    private val expTable = IntArray(512)
    private val logTable = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            expTable[i] = x
            expTable[i + 255] = x
            logTable[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) {
                x = x xor 0x11D
            }
        }
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return expTable[logTable[x] + logTable[y]]
    }

    private fun calculateReedSolomon(data: ByteArray, ecCount: Int): ByteArray {
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val root = expTable[i]
            val nextGen = IntArray(gen.size + 1)
            for (j in gen.indices) {
                nextGen[j] = nextGen[j] xor gfMul(gen[j], root)
                nextGen[j + 1] = nextGen[j + 1] xor gen[j]
            }
            gen = nextGen
        }

        val result = IntArray(ecCount)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor result[0]
            for (i in 0 until ecCount - 1) {
                result[i] = result[i + 1] xor gfMul(gen[gen.size - 2 - i], factor)
            }
            result[ecCount - 1] = gfMul(gen[0], factor)
        }

        return ByteArray(ecCount) { result[it].toByte() }
    }
}
