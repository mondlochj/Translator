package com.arosys.meetingassistant.accelerator

sealed class OrtBackend {

    /** Pure ORT CPU execution — always available, baseline. */
    data object Cpu : OrtBackend()

    /**
     * XNNPACK-accelerated CPU.  Significantly faster than vanilla CPU for
     * float32/float16 convolutions and matmuls on ARM64.
     */
    data object Xnnpack : OrtBackend()

    /**
     * Android Neural Networks API.  Routes ops to NPU/DSP/GPU depending on
     * what the SoC vendor exposes via NNAPI.  On Snapdragon 8 Gen 2/3 this
     * targets the Hexagon NPU.
     *
     * @param flags  Bitmask of [NnapiFlags] values.
     */
    data class Nnapi(val flags: Int = NnapiFlags.USE_FP16) : OrtBackend()

    val displayName: String
        get() = when (this) {
            Cpu    -> "CPU"
            Xnnpack -> "XNNPACK (CPU)"
            is Nnapi -> "NNAPI"
        }

    val shortName: String
        get() = when (this) {
            Cpu     -> "cpu"
            Xnnpack -> "xnnpack"
            is Nnapi -> "nnapi"
        }
}

object NnapiFlags {
    /** Enable FP16 precision — typically faster on NPU/DSP. */
    const val NONE = 0
    const val USE_FP16 = 1
    const val USE_NCHW = 2
    /** Force NPU/DSP/GPU; fail any op that can't run there (no CPU fallback). */
    const val CPU_DISABLED = 4
    /** Route everything through NNAPI CPU path — useful only for debugging. */
    const val CPU_ONLY = 8

    /** Production default: FP16 + allow CPU fallback for unsupported ops. */
    const val DEFAULT = USE_FP16
}

/** All backends in deterministic probe order (cheapest first). */
val ALL_BACKENDS: List<OrtBackend> = listOf(
    OrtBackend.Cpu,
    OrtBackend.Xnnpack,
    OrtBackend.Nnapi(NnapiFlags.DEFAULT),
)
