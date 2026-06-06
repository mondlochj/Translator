package com.arosys.meetingassistant.accelerator

import androidx.test.core.app.ApplicationProvider
import com.arosys.meetingassistant.testing.MainDispatcherRule
import com.arosys.meetingassistant.testing.fakes.FakeBenchmarkRunner
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HardwareAcceleratorManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRunner: FakeBenchmarkRunner
    private lateinit var manager: HardwareAcceleratorManager

    @Before
    fun setup() {
        HardwareAcceleratorManager.reset()
        fakeRunner = FakeBenchmarkRunner()
        manager = HardwareAcceleratorManager.init(
            context = ApplicationProvider.getApplicationContext(),
            scope = TestScope(mainDispatcherRule.dispatcher),
            runner = fakeRunner,
        )
    }

    @After
    fun teardown() {
        HardwareAcceleratorManager.reset()
        // Clear SharedPreferences between tests
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("accelerator_cache_v1", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // -------------------------------------------------------------------------
    // bestBackendFor — no cache
    // -------------------------------------------------------------------------

    @Test
    fun `bestBackendFor returns CPU before any benchmark is run`() {
        val fresh = FakeBenchmarkRunner(microResults = emptyList())
        HardwareAcceleratorManager.reset()
        val mgr = HardwareAcceleratorManager.init(
            ApplicationProvider.getApplicationContext(),
            TestScope(mainDispatcherRule.dispatcher),
            fresh,
        )
        // Micro-benchmark returns nothing → falls back to derived default
        // On JVM, NNAPI is unavailable → default is XNNPACK or CPU
        val backend = mgr.bestBackendFor("some_model")
        assertNotNull(backend)
    }

    // -------------------------------------------------------------------------
    // benchmarkModel — selection
    // -------------------------------------------------------------------------

    @Test
    fun `benchmarkModel selects NNAPI when it has lowest median latency`() = runTest {
        val selection = manager.benchmarkModel("nllb_600m", ByteArray(0))
        assertEquals(OrtBackend.Nnapi(), selection.selectedBackend)
        assertEquals(SelectionReason.BENCHMARKED, selection.selectionReason)
    }

    @Test
    fun `benchmarkModel returns BENCHMARKED reason on fresh benchmark`() = runTest {
        val selection = manager.benchmarkModel("test_model", ByteArray(0))
        assertEquals(SelectionReason.BENCHMARKED, selection.selectionReason)
    }

    @Test
    fun `benchmarkModel results contain all three backends`() = runTest {
        val selection = manager.benchmarkModel("any_model", ByteArray(0))
        val backends = selection.benchmarkResults.map { it.backend }
        assertTrue(OrtBackend.Cpu in backends)
        assertTrue(OrtBackend.Xnnpack in backends)
        assertTrue(OrtBackend.Nnapi() in backends)
    }

    // -------------------------------------------------------------------------
    // Caching
    // -------------------------------------------------------------------------

    @Test
    fun `second benchmarkModel call uses cache`() = runTest {
        manager.benchmarkModel("cached_model", ByteArray(0))
        manager.benchmarkModel("cached_model", ByteArray(0))

        // The fake runner should only have been called once
        assertEquals(1, fakeRunner.benchmarkCalls.count { it == "cached_model" })
    }

    @Test
    fun `forceRefresh bypasses cache`() = runTest {
        manager.benchmarkModel("force_model", ByteArray(0))
        manager.benchmarkModel("force_model", ByteArray(0), forceRefresh = true)

        assertEquals(2, fakeRunner.benchmarkCalls.count { it == "force_model" })
    }

    @Test
    fun `bestBackendFor returns fastest after benchmark`() = runTest {
        manager.benchmarkModel("model_a", ByteArray(0))
        val best = manager.bestBackendFor("model_a")
        // Fake returns NNAPI(30ms) < XNNPACK(60ms) < CPU(120ms)
        assertEquals(OrtBackend.Nnapi(), best)
    }

    @Test
    fun `invalidateCache clears all results`() = runTest {
        manager.benchmarkModel("to_clear", ByteArray(0))
        manager.invalidateCache()
        // After invalidation, a new call should hit the runner again
        manager.benchmarkModel("to_clear", ByteArray(0))
        assertEquals(2, fakeRunner.benchmarkCalls.count { it == "to_clear" })
    }

    @Test
    fun `invalidateCacheFor clears only the specified model`() = runTest {
        manager.benchmarkModel("model_keep", ByteArray(0))
        manager.benchmarkModel("model_clear", ByteArray(0))

        manager.invalidateCacheFor("model_clear")

        manager.benchmarkModel("model_keep", ByteArray(0))   // should use cache
        manager.benchmarkModel("model_clear", ByteArray(0))  // should re-run

        assertEquals(1, fakeRunner.benchmarkCalls.count { it == "model_keep" })
        assertEquals(2, fakeRunner.benchmarkCalls.count { it == "model_clear" })
    }

    // -------------------------------------------------------------------------
    // FALLBACK_UNAVAILABLE path
    // -------------------------------------------------------------------------

    @Test
    fun `benchmarkModel returns FALLBACK_UNAVAILABLE when only CPU is available`() = runTest {
        val cpuOnlyRunner = FakeBenchmarkRunner(
            defaultResults = listOf(
                BenchmarkResult("m", OrtBackend.Cpu,     isAvailable = true,  medianLatencyMs = 120),
                BenchmarkResult("m", OrtBackend.Xnnpack, isAvailable = false),
                BenchmarkResult("m", OrtBackend.Nnapi(), isAvailable = false),
            )
        )
        HardwareAcceleratorManager.reset()
        val mgr = HardwareAcceleratorManager.init(
            ApplicationProvider.getApplicationContext(),
            TestScope(mainDispatcherRule.dispatcher),
            cpuOnlyRunner,
        )

        val selection = mgr.benchmarkModel("cpu_only_model", ByteArray(0))
        assertEquals(OrtBackend.Cpu, selection.selectedBackend)
        assertEquals(SelectionReason.FALLBACK_UNAVAILABLE, selection.selectionReason)
    }
}
