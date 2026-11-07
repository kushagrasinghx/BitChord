package com.music.bitchord.playback.smart

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** The pinned weights and float tensor contract of one shipped model. */
class ModelAsset(
    val name: String,
    val bytes: Long,
    val sha256: String,
    val input: String,
    val inputShape: LongArray,
    val outputs: Map<String, LongArray>,
) {
    fun validate(session: OrtSession) {
        require(session.inputNames == setOf(input)) { "$name: unexpected inputs" }
        require(session.outputNames == outputs.keys) { "$name: unexpected outputs" }
        fun validateTensor(info: TensorInfo, expected: LongArray, output: Boolean = false) {
            val matches = info.shape.size == expected.size && info.shape.indices.all { index ->
                info.shape[index] == expected[index] || (output && index == 0 && expected[index] == -1L && info.shape[index] == 1L)
            }
            require(info.type == OnnxJavaType.FLOAT && matches) {
                "$name: unexpected float tensor shape ${info.shape.contentToString()}"
            }
        }
        validateTensor(session.inputInfo.getValue(input).info as TensorInfo, inputShape)
        outputs.forEach { (name, shape) ->
            validateTensor(session.outputInfo.getValue(name).info as TensorInfo, shape, output = true)
        }
    }

    fun matches(file: File): Boolean = file.isFile && file.length() == bytes &&
        file.inputStream().use { checksum(it) } == sha256

    /** Replace stale or interrupted APK extraction before ONNX Runtime opens the file. */
    fun unpack(directory: File, source: () -> InputStream): String {
        require(name.matches(Regex("[a-zA-Z0-9_.-]+\\.onnx")))
        val file = File(directory, name)
        if (matches(file)) return file.absolutePath
        directory.mkdirs()
        val temporary = File.createTempFile(name, ".tmp", directory)
        try {
            source().use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            require(matches(temporary)) { "$name: packaged model checksum mismatch" }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
        return file.absolutePath
    }

    private fun checksum(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
