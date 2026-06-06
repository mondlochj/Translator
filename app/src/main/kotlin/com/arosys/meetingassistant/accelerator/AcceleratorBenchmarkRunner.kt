package com.arosys.meetingassistant.accelerator

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

private const val TAG = "AcceleratorBenchmark"
private const val WARMUP_ITERATIONS = 3
private const val TIMED_ITERATIONS = 7

/**
 * Seam interface allowing [HardwareAcceleratorManager] to be tested without
 * a real ORT runtime.  Production code uses [AcceleratorBenchmarkRunner];
 * tests inject [com.arosys.meetingassistant.testing.fakes.FakeBenchmarkRunner].
 */
interface BenchmarkRunner {
    suspend fun probeAvailableBackends(modelBytes: ByteArray): List<BackendProbeResult>
    suspend fun benchmark(
        modelName: String,
        modelBytes: ByteArray,
        config: SessionConfig = SessionConfig(),
    ): List<BenchmarkResult>
    suspend fun runMicroBenchmark(): List<BenchmarkResult>
}

/**
 * Runs inference benchmarks against each [OrtBackend] and returns
 * a [BenchmarkResult] per backend.
 *
 * Two modes:
 *  - **Micro-benchmark**: uses the tiny `benchmark_model.onnx` shipped in
 *    assets.  Run once at app start to get a fast relative ranking.
 *  - **Model-benchmark**: uses the actual production model bytes.  Run on
 *    first model load to get real-world numbers and update the cache.
 */
class AcceleratorBenchmarkRunner(private val context: Context) : BenchmarkRunner {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    // -------------------------------------------------------------------------
    // Backend availability probing
    // -------------------------------------------------------------------------

    /**
     * Checks which backends can actually create a session.
     * Fast — no inference, just session creation + immediate close.
     */
    suspend fun probeAvailableBackends(modelBytes: ByteArray): List<BackendProbeResult> =
        withContext(Dispatchers.IO) {
            ALL_BACKENDS.map { backend -> probeBackend(backend, modelBytes) }
        }

    private fun probeBackend(backend: OrtBackend, modelBytes: ByteArray): BackendProbeResult {
        if (backend is OrtBackend.Nnapi && !isNnapiAvailable()) {
            return BackendProbeResult(backend, false, "NNAPI requires API 27+; device is API ${Build.VERSION.SDK_INT}")
        }
        return try {
            OrtSessionFactory.create(modelBytes, backend).use { /* session created successfully */ }
            BackendProbeResult(backend, true)
        } catch (e: Exception) {
            BackendProbeResult(backend, false, e.message)
        }
    }

    // -------------------------------------------------------------------------
    // Benchmarking
    // -------------------------------------------------------------------------

    /**
     * Benchmarks [modelBytes] on all available backends.
     * The model's input names/shapes must already be known; this function
     * creates zero-filled tensors matching the first input of the model.
     */
    suspend fun benchmark(
        modelName: String,
        modelBytes: ByteArray,
        config: SessionConfig = SessionConfig(),
    ): List<BenchmarkResult> = withContext(Dispatchers.IO) {
        val probes = probeAvailableBackends(modelBytes)
        probes.map { probe ->
            if (!probe.isAvailable) {
                BenchmarkResult(
                    modelName = modelName,
                    backend = probe.backend,
                    isAvailable = false,
                    errorMessage = probe.unavailableReason,
                )
            } else {
                runBenchmark(modelName, modelBytes, probe.backend, config)
            }
        }
    }

    /**
     * Loads the micro-benchmark model from assets and runs [benchmark].
     * Returns empty list if the asset does not exist yet (user has not run
     * the generate script).
     */
    suspend fun runMicroBenchmark(): List<BenchmarkResult> = withContext(Dispatchers.IO) {
        val bytes = loadBenchmarkAsset() ?: run {
            Log.w(TAG, "benchmark_model.onnx not found in assets — skipping micro-benchmark. " +
                "Run scripts/generate_benchmark_model.py to generate it.")
            return@withContext emptyList()
        }
        benchmark(MICRO_BENCHMARK_MODEL_NAME, bytes)
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun runBenchmark(
        modelName: String,
        modelBytes: ByteArray,
        backend: OrtBackend,
        config: SessionConfig,
    ): BenchmarkResult {
        return try {
            OrtSessionFactory.create(modelBytes, backend, config).use { session ->
                val inputNames = session.inputNames.toList()
                val inputInfo = session.inputInfo

                val dummyInputs = buildDummyInputs(inputNames, inputInfo)
                val latencies = LongArray(WARMUP_ITERATIONS + TIMED_ITERATIONS)

                try {
                    repeat(WARMUP_ITERATIONS + TIMED_ITERATIONS) { i ->
                        latencies[i] = measureTimeMillis {
                            session.run(dummyInputs).close()
                        }
                    }
                } finally {
                    dummyInputs.values.forEach { it.close() }
                }

                val timed = latencies.drop(WARMUP_ITERATIONS).sorted()
                val median = timed[timed.size / 2]
                val p95 = timed[(timed.size * 95 / 100).coerceAtMost(timed.size - 1)]

                Log.i(TAG, "[$modelName] ${backend.displayName}: median=${median}ms p95=${p95}ms")

                BenchmarkResult(
                    modelName = modelName,
                    backend = backend,
                    isAvailable = true,
                    medianLatencyMs = median,
                    p95LatencyMs = p95,
                    iterationCount = TIMED_ITERATIONS,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Benchmark failed for ${backend.displayName}: ${e.message}")
            BenchmarkResult(
                modelName = modelName,
                backend = backend,
                isAvailable = false,
                errorMessage = e.message,
            )
        }
    }

    private fun buildDummyInputs(
        inputNames: List<String>,
        inputInfo: Map<String, ai.onnxruntime.NodeInfo>,
    ): Map<String, OnnxTensor> {
        return inputNames.associateWith { name ->
            val info = inputInfo[name]
            val shape = extractShape(info)
            OnnxTensor.createTensor(env, FloatArray(shape.reduce(Long::times).toInt()), shape)
        }
    }

    private fun extractShape(nodeInfo: ai.onnxruntime.NodeInfo?): LongArray {
        if (nodeInfo == null) return longArrayOf(1, 64)
        return try {
            val tensorInfo = nodeInfo.info as? ai.onnxruntime.TensorInfo
                ?: return longArrayOf(1, 64)
            tensorInfo.shape.map { dim -> if (dim <= 0) 1L else dim }.toLongArray()
        } catch (e: Exception) {
            longArrayOf(1, 64)
        }
    }

    private fun loadBenchmarkAsset(): ByteArray? {
        return try {
            context.assets.open("benchmark_model.onnx").use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val MICRO_BENCHMARK_MODEL_NAME = "__micro_benchmark__"

        fun isNnapiAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 // API 27

        /** Selects the backend with the lowest median latency from a list of results. */
        fun selectFastest(results: List<BenchmarkResult>): OrtBackend {
            return results
                .filter { it.isAvailable && it.medianLatencyMs > 0 }
                .minByOrNull { it.medianLatencyMs }
                ?.backend
                ?: OrtBackend.Cpu
        }
    }
}
