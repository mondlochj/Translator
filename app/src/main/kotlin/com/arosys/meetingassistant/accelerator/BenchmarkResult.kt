package com.arosys.meetingassistant.accelerator

/**
 * Timing result for one (model, backend) pair.
 * Collected by [AcceleratorBenchmarkRunner] and cached by [HardwareAcceleratorManager].
 */
data class BenchmarkResult(
    val modelName: String,
    val backend: OrtBackend,
    val isAvailable: Boolean,
    /** Median inference latency across timed iterations, -1 if not measured. */
    val medianLatencyMs: Long = -1,
    /** 95th-percentile latency, -1 if not measured. */
    val p95LatencyMs: Long = -1,
    /** Number of timed iterations that produced this result. */
    val iterationCount: Int = 0,
    val errorMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class BackendProbeResult(
    val backend: OrtBackend,
    val isAvailable: Boolean,
    val unavailableReason: String? = null,
)

/** Summary of the best backend chosen for a given model. */
data class BackendSelection(
    val modelName: String,
    val selectedBackend: OrtBackend,
    val selectionReason: SelectionReason,
    val benchmarkResults: List<BenchmarkResult>,
)

enum class SelectionReason {
    BENCHMARKED,          // chose the fastest measured backend
    CACHED,               // re-used a prior benchmark from disk
    FALLBACK_UNAVAILABLE, // NNAPI/XNNPACK not available; CPU used
    FALLBACK_ERROR,       // benchmark threw; CPU used
}
