package com.arosys.meetingassistant.testing.fakes

import com.arosys.meetingassistant.accelerator.BackendProbeResult
import com.arosys.meetingassistant.accelerator.BenchmarkResult
import com.arosys.meetingassistant.accelerator.BenchmarkRunner
import com.arosys.meetingassistant.accelerator.OrtBackend
import com.arosys.meetingassistant.accelerator.SessionConfig

/**
 * Test double for [BenchmarkRunner].  Allows tests to control benchmark
 * outcomes without an ORT runtime or real model bytes.
 *
 * Default behaviour: reports all three backends as available with
 * NNAPI fastest (30 ms), XNNPACK second (60 ms), CPU slowest (120 ms).
 * Override [resultsFor] to customise per model name.
 */
class FakeBenchmarkRunner(
    private val defaultResults: List<BenchmarkResult> = listOf(
        BenchmarkResult("__model__", OrtBackend.Cpu,     isAvailable = true,  medianLatencyMs = 120, p95LatencyMs = 140),
        BenchmarkResult("__model__", OrtBackend.Xnnpack, isAvailable = true,  medianLatencyMs = 60,  p95LatencyMs = 75),
        BenchmarkResult("__model__", OrtBackend.Nnapi(), isAvailable = true,  medianLatencyMs = 30,  p95LatencyMs = 40),
    ),
    private val microResults: List<BenchmarkResult> = emptyList(),
) : BenchmarkRunner {

    /** Override per model name. Key = modelName, value = list to return. */
    val resultsFor: MutableMap<String, List<BenchmarkResult>> = mutableMapOf()

    /** Call history for assertions. */
    val benchmarkCalls = mutableListOf<String>()
    val probeCount get() = _probeCount
    private var _probeCount = 0

    override suspend fun probeAvailableBackends(modelBytes: ByteArray): List<BackendProbeResult> {
        _probeCount++
        return defaultResults.map { BackendProbeResult(it.backend, it.isAvailable) }
    }

    override suspend fun benchmark(
        modelName: String,
        modelBytes: ByteArray,
        config: SessionConfig,
    ): List<BenchmarkResult> {
        benchmarkCalls += modelName
        val template = resultsFor[modelName] ?: defaultResults
        return template.map { it.copy(modelName = modelName) }
    }

    override suspend fun runMicroBenchmark(): List<BenchmarkResult> = microResults
}
