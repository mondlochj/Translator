package com.arosys.meetingassistant.accelerator

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "HardwareAcceleratorMgr"
private const val PREFS_NAME = "accelerator_cache_v1"
private const val PREFS_KEY_RESULTS = "benchmark_results"
private const val CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

/**
 * Application-scoped singleton that:
 *  1. Detects which [OrtBackend]s are available on this device.
 *  2. Runs the micro-benchmark at startup to get a relative speed ranking.
 *  3. Caches per-model benchmark results in SharedPreferences (7-day TTL).
 *  4. Exposes [bestBackendFor] so every model implementation can ask "which
 *     backend should I use?" without knowing anything about the hardware.
 *
 * Usage:
 * ```
 * // In Application.onCreate():
 * HardwareAcceleratorManager.init(this, applicationScope)
 *
 * // In any model impl:
 * val backend = HardwareAcceleratorManager.instance.bestBackendFor("nllb_600m")
 * val session = OrtSessionFactory.create(modelBytes, backend)
 * ```
 */
class HardwareAcceleratorManager private constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val runner: BenchmarkRunner,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _state = MutableStateFlow(AcceleratorState())
    val state: StateFlow<AcceleratorState> = _state.asStateFlow()

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    fun initialize() {
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(status = AcceleratorStatus.PROBING) }

            val deviceInfo = buildDeviceInfo()
            Log.i(TAG, "Device: ${deviceInfo.socManufacturer} ${deviceInfo.socModel}, " +
                "API ${deviceInfo.apiLevel}, NNAPI available: ${deviceInfo.nnapiAvailable}")

            _state.update { it.copy(deviceInfo = deviceInfo) }

            // Run micro-benchmark for initial backend ranking
            val microResults = runner.runMicroBenchmark()
            if (microResults.isNotEmpty()) {
                cacheResults(microResults)
                val fastest = AcceleratorBenchmarkRunner.selectFastest(microResults)
                Log.i(TAG, "Micro-benchmark fastest backend: ${fastest.displayName}")
                _state.update {
                    it.copy(
                        microBenchmarkResults = microResults,
                        defaultBackend = fastest,
                        status = AcceleratorStatus.READY,
                    )
                }
            } else {
                // No benchmark model asset — derive a sensible default from device info
                val defaultBackend = deriveDefaultBackend(deviceInfo)
                Log.i(TAG, "No micro-benchmark model; derived default: ${defaultBackend.displayName}")
                _state.update {
                    it.copy(
                        defaultBackend = defaultBackend,
                        status = AcceleratorStatus.READY,
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the best cached backend for [modelName].
     * If no cache entry exists, returns [state.value.defaultBackend].
     * Does **not** trigger a new benchmark — call [benchmarkModel] for that.
     */
    fun bestBackendFor(modelName: String): OrtBackend {
        val cached = loadCachedResults(modelName)
        if (cached.isNotEmpty()) {
            val best = AcceleratorBenchmarkRunner.selectFastest(cached)
            Log.d(TAG, "[$modelName] cached best: ${best.displayName}")
            return best
        }
        return _state.value.defaultBackend
    }

    /**
     * Benchmarks [modelBytes] on all available backends and caches the results.
     * Subsequent calls to [bestBackendFor] will use these results.
     *
     * Safe to call multiple times; results are refreshed only when the cache is
     * expired (> 7 days old).
     *
     * @return [BackendSelection] describing what was chosen and why.
     */
    suspend fun benchmarkModel(
        modelName: String,
        modelBytes: ByteArray,
        forceRefresh: Boolean = false,
    ): BackendSelection {
        val cached = loadCachedResults(modelName)
        if (cached.isNotEmpty() && !forceRefresh && !isCacheExpired(cached)) {
            Log.i(TAG, "[$modelName] using cached benchmark results")
            return BackendSelection(
                modelName = modelName,
                selectedBackend = AcceleratorBenchmarkRunner.selectFastest(cached),
                selectionReason = SelectionReason.CACHED,
                benchmarkResults = cached,
            )
        }

        Log.i(TAG, "[$modelName] running full benchmark…")
        _state.update { it.copy(activeBenchmarkModel = modelName) }

        val results = try {
            runner.benchmark(modelName, modelBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Benchmark threw for $modelName", e)
            return BackendSelection(
                modelName = modelName,
                selectedBackend = OrtBackend.Cpu,
                selectionReason = SelectionReason.FALLBACK_ERROR,
                benchmarkResults = emptyList(),
            )
        } finally {
            _state.update { it.copy(activeBenchmarkModel = null) }
        }

        cacheResults(results)
        _state.update { current ->
            val updated = current.modelBenchmarkResults.toMutableMap().apply {
                put(modelName, results)
            }
            current.copy(modelBenchmarkResults = updated)
        }

        val fastest = AcceleratorBenchmarkRunner.selectFastest(results)
        logBenchmarkSummary(modelName, results, fastest)

        val available = results.any { it.isAvailable && it.backend != OrtBackend.Cpu }
        return BackendSelection(
            modelName = modelName,
            selectedBackend = fastest,
            selectionReason = if (available) SelectionReason.BENCHMARKED else SelectionReason.FALLBACK_UNAVAILABLE,
            benchmarkResults = results,
        )
    }

    /** Clears all cached benchmark results. */
    fun invalidateCache() {
        prefs.edit().remove(PREFS_KEY_RESULTS).apply()
        _state.update { it.copy(modelBenchmarkResults = emptyMap()) }
        Log.i(TAG, "Benchmark cache cleared")
    }

    /** Clears cached results for a specific model. */
    fun invalidateCacheFor(modelName: String) {
        val all = loadAllCachedResults().toMutableMap()
        all.remove(modelName)
        saveAllCachedResults(all)
        _state.update { current ->
            current.copy(modelBenchmarkResults = current.modelBenchmarkResults - modelName)
        }
    }

    // -------------------------------------------------------------------------
    // Cache helpers
    // -------------------------------------------------------------------------

    private fun loadCachedResults(modelName: String): List<BenchmarkResult> {
        return loadAllCachedResults()[modelName] ?: emptyList()
    }

    private fun loadAllCachedResults(): Map<String, List<BenchmarkResult>> {
        val json = prefs.getString(PREFS_KEY_RESULTS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, List<BenchmarkResult>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize benchmark cache: ${e.message}")
            emptyMap()
        }
    }

    private fun cacheResults(results: List<BenchmarkResult>) {
        if (results.isEmpty()) return
        val all = loadAllCachedResults().toMutableMap()
        all[results.first().modelName] = results
        saveAllCachedResults(all)
    }

    private fun saveAllCachedResults(all: Map<String, List<BenchmarkResult>>) {
        prefs.edit().putString(PREFS_KEY_RESULTS, gson.toJson(all)).apply()
    }

    private fun isCacheExpired(results: List<BenchmarkResult>): Boolean {
        val oldest = results.minOfOrNull { it.timestampMs } ?: return true
        return (System.currentTimeMillis() - oldest) > CACHE_TTL_MS
    }

    // -------------------------------------------------------------------------
    // Device info
    // -------------------------------------------------------------------------

    private fun buildDeviceInfo(): DeviceInfo {
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER ?: "Unknown"
        } else {
            Build.HARDWARE ?: "Unknown"
        }
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL ?: "Unknown"
        } else {
            Build.HARDWARE ?: "Unknown"
        }
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            socManufacturer = socManufacturer,
            socModel = socModel,
            apiLevel = Build.VERSION.SDK_INT,
            nnapiAvailable = AcceleratorBenchmarkRunner.isNnapiAvailable(),
            isLikelyQualcomm = socManufacturer.lowercase().contains("qualcomm"),
            isLikelyExynos = socModel.lowercase().contains("exynos") ||
                Build.HARDWARE?.lowercase()?.contains("exynos") == true,
        )
    }

    private fun deriveDefaultBackend(device: DeviceInfo): OrtBackend {
        return when {
            !device.nnapiAvailable -> OrtBackend.Xnnpack
            // On Qualcomm Snapdragon (Galaxy Fold 4/5/6), NNAPI routes to Hexagon NPU
            device.isLikelyQualcomm -> OrtBackend.Nnapi(NnapiFlags.DEFAULT)
            device.nnapiAvailable   -> OrtBackend.Nnapi(NnapiFlags.DEFAULT)
            else                    -> OrtBackend.Xnnpack
        }
    }

    private fun logBenchmarkSummary(
        modelName: String,
        results: List<BenchmarkResult>,
        fastest: OrtBackend,
    ) {
        Log.i(TAG, "=== Benchmark results for [$modelName] ===")
        results.forEach { r ->
            if (r.isAvailable) {
                Log.i(TAG, "  ${r.backend.displayName.padEnd(16)}: median=${r.medianLatencyMs}ms  p95=${r.p95LatencyMs}ms")
            } else {
                Log.i(TAG, "  ${r.backend.displayName.padEnd(16)}: UNAVAILABLE (${r.errorMessage})")
            }
        }
        Log.i(TAG, "  Selected: ${fastest.displayName}")
    }

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    companion object {
        @Volatile private var _instance: HardwareAcceleratorManager? = null

        val instance: HardwareAcceleratorManager
            get() = _instance ?: error("HardwareAcceleratorManager not initialized. Call init() in Application.onCreate().")

        fun init(context: Context, scope: CoroutineScope): HardwareAcceleratorManager =
            init(context, scope, AcceleratorBenchmarkRunner(context.applicationContext))

        internal fun init(
            context: Context,
            scope: CoroutineScope,
            runner: BenchmarkRunner,
        ): HardwareAcceleratorManager {
            return _instance ?: synchronized(this) {
                _instance ?: HardwareAcceleratorManager(
                    context = context.applicationContext,
                    scope = scope,
                    runner = runner,
                ).also {
                    _instance = it
                    it.initialize()
                }
            }
        }

        /** Resets the singleton — only for use in tests. */
        internal fun reset() {
            _instance = null
        }
    }
}

// -------------------------------------------------------------------------
// State types
// -------------------------------------------------------------------------

enum class AcceleratorStatus { IDLE, PROBING, READY }

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val socManufacturer: String,
    val socModel: String,
    val apiLevel: Int,
    val nnapiAvailable: Boolean,
    val isLikelyQualcomm: Boolean,
    val isLikelyExynos: Boolean,
)

data class AcceleratorState(
    val status: AcceleratorStatus = AcceleratorStatus.IDLE,
    val deviceInfo: DeviceInfo? = null,
    val defaultBackend: OrtBackend = OrtBackend.Cpu,
    val microBenchmarkResults: List<BenchmarkResult> = emptyList(),
    val modelBenchmarkResults: Map<String, List<BenchmarkResult>> = emptyMap(),
    val activeBenchmarkModel: String? = null,
)
