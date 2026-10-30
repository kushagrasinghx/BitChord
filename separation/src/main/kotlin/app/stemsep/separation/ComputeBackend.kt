package app.stemsep.separation

import android.os.Build

enum class ComputeBackend(
    val cacheId: String,
    val displayName: String,
) {
    CPU("cpu", "CPU"),
    SNAPDRAGON_NNAPI("nnapi", "Snapdragon GPU/NPU");

    companion object {
        fun fromId(id: String?): ComputeBackend =
            entries.firstOrNull { it.cacheId == id } ?: CPU
    }
}

enum class AcceleratorMode(
    val cacheId: String,
    val displayName: String,
    val description: String,
) {
    BALANCED(
        "balanced",
        "Balanced",
        "NNAPI may use GPU, NPU or CPU per operation and may fall back to CPU.",
    ),
    ACCELERATOR_ONLY(
        "accelerator",
        "Accelerator only",
        "Disables NNAPI CPU fallback. If the driver cannot run the model, the job fails instead of silently using CPU.",
    );

    companion object {
        fun fromId(id: String?): AcceleratorMode =
            entries.firstOrNull { it.cacheId == id } ?: BALANCED
    }
}

data class ComputeCapabilities(
    val snapdragon: Boolean,
    val arm64: Boolean,
    val nnapi: Boolean,
    val socManufacturer: String,
    val socModel: String,
    val cpuCores: Int,
) {
    val canUseSnapdragonNnapi: Boolean
        get() = snapdragon && arm64 && nnapi

    val socLabel: String
        get() = listOf(socManufacturer, socModel)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "unknown SoC" }

    fun supports(backend: ComputeBackend): Boolean = when (backend) {
        ComputeBackend.CPU -> true
        ComputeBackend.SNAPDRAGON_NNAPI -> canUseSnapdragonNnapi
    }
}

object ComputeCapabilityDetector {
    fun detect(): ComputeCapabilities {
        val manufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER
        } else {
            Build.MANUFACTURER
        }
        val model = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            Build.BOARD
        }
        return ComputeCapabilities(
            snapdragon = isSnapdragonSoc(manufacturer, Build.HARDWARE, Build.BOARD, model),
            arm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
            nnapi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1,
            socManufacturer = manufacturer.orEmpty(),
            socModel = model.orEmpty(),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
        )
    }

    internal fun isSnapdragonSoc(
        manufacturer: String?,
        hardware: String?,
        board: String?,
        socModel: String?,
    ): Boolean {
        val manufacturerText = manufacturer.orEmpty().lowercase()
        val hardwareText = hardware.orEmpty().lowercase()
        val boardText = board.orEmpty().lowercase()
        if (
            manufacturerText.contains("qualcomm") ||
            manufacturerText.contains("qcom") ||
            hardwareText.contains("qcom") ||
            hardwareText.contains("qualcomm") ||
            boardText.contains("qcom")
        ) {
            return true
        }
        return SNAPDRAGON_MODEL_PATTERN.containsMatchIn(socModel.orEmpty())
    }

    private val SNAPDRAGON_MODEL_PATTERN =
        Regex("^(sm|msm|apq|qcm|qcs|sda|sdm)\\d", RegexOption.IGNORE_CASE)
}
