package com.music.bitchord.playback.smart

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelAssetTest {
    @Test fun bundledModelsMatchTheirChecksumsAndRuntimeContracts() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "native/analyzer/model-manifest.json").isFile }
        for (spec in listOf(AutomixModels.BEAT, AutomixModels.VOCAL)) {
            val file = File(root, "app/src/main/assets/${spec.name}")
            assertTrue(spec.matches(file))
            OrtSession.SessionOptions().use { options ->
                OrtEnvironment.getEnvironment().createSession(file.absolutePath, options).use { spec.validate(it) }
            }
        }
    }

    @Test fun corruptExtractionIsReplacedAndBadPackagedBytesAreRefused() {
        val directory = Files.createTempDirectory("bitchord-model-test-").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            val spec = ModelAsset("test.onnx", bytes.size.toLong(), sha, "input", longArrayOf(1), emptyMap())
            val file = File(directory, spec.name)
            file.writeBytes(byteArrayOf(4, 3, 2, 1)) // Same length, different weights.
            spec.unpack(directory) { ByteArrayInputStream(bytes) }
            assertTrue(spec.matches(file))
            spec.unpack(directory) { error("A matching extraction must be reused") }
            file.writeBytes(byteArrayOf(4, 3, 2, 1))
            assertFailsWith<IllegalArgumentException> {
                spec.unpack(directory) { ByteArrayInputStream(byteArrayOf(0, 0, 0, 0)) }
            }
            assertEquals(listOf(spec.name), directory.listFiles()!!.map { it.name })
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
