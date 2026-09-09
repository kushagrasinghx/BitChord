package com.example.liquidglass

enum class BlurMethod {
    BOX_BLUR,
    BOX_BLUR_CPP,
    IIR_GAUSSIAN,
    IIR_GAUSSIAN_NEON,
    BOX3,
    SMART,
    DOWNSAMPLE,
}
