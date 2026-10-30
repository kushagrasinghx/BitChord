package app.stemsep.separation

import app.stemsep.dsp.MdxConfig

data class ModelSpec(
    val id: String,
    val displayName: String,
    val description: String,
    val url: String,
    val fallbackUrl: String?,
    /**
     * Pinned SHA-256, or null for a user-supplied model whose hash cannot be
     * known in advance. Unverified models are still hash-checked once
     * downloaded — see `ModelDownloader`'s sidecar file — and are labelled as
     * unverified in the debug panel.
     */
    val sha256: String?,
    val sizeBytes: Long,
    val config: MdxConfig,
)

object ModelCatalog {
    const val DEFAULT_MODEL_ID = "mdx-9482"
    const val SEPARATOR_VERSION = 3

    val mdx9482 = ModelSpec(
        id = DEFAULT_MODEL_ID,
        displayName = "UVR_MDXNET_9482",
        description = "Fast UVR MDX-Net model for everyday tracks.",
        url = "https://github.com/nomadkaraoke/python-audio-separator/releases/download/model-configs/UVR_MDXNET_9482.onnx",
        fallbackUrl = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_9482.onnx",
        sha256 = "f4f365207c56deb115bceedff3ad8fe98a751c745f9e370cecec6226b8b47184",
        sizeBytes = 29_704_436L,
        config = MdxConfig(
            fftSize = 6144,
            hopLength = 1024,
            frequencyBins = 2048,
            timeFrames = 256,
            segmentSamples = 261_120,
            edgeTrimSamples = 3072,
            compensation = 1.035f,
            overlap = 0.10f,
            sampleRate = 44_100,
            zeroLowBins = true,
        ),
    )

    val all: List<ModelSpec> = listOf(mdx9482)

    fun byId(id: String?): ModelSpec = all.firstOrNull { it.id == id } ?: mdx9482

    /**
     * A user-supplied model from the settings screen. BitChord only runs MDX
     * -Net graphs, so the tensor geometry stays the pinned one; anything else
     * fails at session creation, which the settings/download UI surfaces.
     *
     * [sha256] blank means "download without verification"; the computed hash
     * is still recorded next to the file and shown, so it can be pinned later.
     */
    fun custom(url: String, sha256: String? = null, sizeBytes: Long = 0L): ModelSpec {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://")) { "Custom model URLs must be HTTPS" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(trimmed.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12)
        val name = trimmed.substringAfterLast('/').substringBefore('?').ifBlank { trimmed }
        return ModelSpec(
            id = "custom-$digest",
            displayName = name,
            description = if (sha256.isNullOrBlank()) {
                "User-supplied model, downloaded without checksum verification."
            } else {
                "User-supplied model with a pinned checksum."
            },
            url = trimmed,
            fallbackUrl = null,
            sha256 = sha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            sizeBytes = sizeBytes.takeIf { it > 0 } ?: 0L,
            config = mdx9482.config,
        )
    }
}
