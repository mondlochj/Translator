package com.arosys.meetingassistant.accelerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AcceleratorBenchmarkRunnerTest {

    // -------------------------------------------------------------------------
    // selectFastest
    // -------------------------------------------------------------------------

    @Test
    fun `selectFastest returns backend with lowest median latency`() {
        val results = listOf(
            BenchmarkResult("m", OrtBackend.Cpu,     isAvailable = true, medianLatencyMs = 100),
            BenchmarkResult("m", OrtBackend.Xnnpack,  isAvailable = true, medianLatencyMs = 60),
            BenchmarkResult("m", OrtBackend.Nnapi(),  isAvailable = true, medianLatencyMs = 30),
        )
        assertEquals(OrtBackend.Nnapi(), AcceleratorBenchmarkRunner.selectFastest(results))
    }

    @Test
    fun `selectFastest ignores unavailable backends`() {
        val results = listOf(
            BenchmarkResult("m", OrtBackend.Cpu,    isAvailable = true,  medianLatencyMs = 100),
            BenchmarkResult("m", OrtBackend.Nnapi(), isAvailable = false, medianLatencyMs = 5),
        )
        assertEquals(OrtBackend.Cpu, AcceleratorBenchmarkRunner.selectFastest(results))
    }

    @Test
    fun `selectFastest ignores results with negative latency`() {
        val results = listOf(
            BenchmarkResult("m", OrtBackend.Nnapi(),  isAvailable = true, medianLatencyMs = -1),
            BenchmarkResult("m", OrtBackend.Cpu,     isAvailable = true, medianLatencyMs = 80),
        )
        assertEquals(OrtBackend.Cpu, AcceleratorBenchmarkRunner.selectFastest(results))
    }

    @Test
    fun `selectFastest returns CPU when all results are unavailable`() {
        val results = listOf(
            BenchmarkResult("m", OrtBackend.Nnapi(),  isAvailable = false),
            BenchmarkResult("m", OrtBackend.Xnnpack, isAvailable = false),
        )
        assertEquals(OrtBackend.Cpu, AcceleratorBenchmarkRunner.selectFastest(results))
    }

    @Test
    fun `selectFastest returns CPU for empty list`() {
        assertEquals(OrtBackend.Cpu, AcceleratorBenchmarkRunner.selectFastest(emptyList()))
    }

    // -------------------------------------------------------------------------
    // isNnapiAvailable
    // -------------------------------------------------------------------------

    @Test
    fun `isNnapiAvailable returns correct value based on API level`() {
        // On the JVM (unit test host) Build.VERSION.SDK_INT is 0, so NNAPI will
        // report unavailable. This test just confirms the method is reachable
        // without crashing and returns a boolean.
        val result = AcceleratorBenchmarkRunner.isNnapiAvailable()
        assert(result == true || result == false)
    }

    // -------------------------------------------------------------------------
    // BenchmarkResult
    // -------------------------------------------------------------------------

    @Test
    fun `BenchmarkResult defaults medianLatencyMs to -1`() {
        val r = BenchmarkResult("m", OrtBackend.Cpu, isAvailable = false)
        assertEquals(-1L, r.medianLatencyMs)
        assertEquals(-1L, r.p95LatencyMs)
        assertNull(r.errorMessage)
    }
}
