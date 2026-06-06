package com.arosys.meetingassistant.accelerator

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log

private const val TAG = "OrtSessionFactory"

data class SessionConfig(
    /** Number of intra-op threads for CPU/XNNPACK execution. */
    val numThreads: Int = 4,
    /** Allow NNAPI to fall back to CPU for ops it can't accelerate. */
    val nnapiAllowCpuFallback: Boolean = true,
    /** Enable graph-level optimizations (recommended for all backends). */
    val graphOptimizationLevel: OrtSession.SessionOptions.OptLevel =
        OrtSession.SessionOptions.OptLevel.ALL_OPT,
)

object OrtSessionFactory {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    /**
     * Creates an [OrtSession] configured for [backend].
     * Throws [OrtSessionCreationException] if the backend is unavailable or the model is invalid.
     */
    fun create(
        modelBytes: ByteArray,
        backend: OrtBackend,
        config: SessionConfig = SessionConfig(),
    ): OrtSession {
        val opts = buildOptions(backend, config)
        return try {
            env.createSession(modelBytes, opts)
        } catch (e: Exception) {
            opts.close()
            throw OrtSessionCreationException(backend, e)
        }
    }

    fun create(
        modelPath: String,
        backend: OrtBackend,
        config: SessionConfig = SessionConfig(),
    ): OrtSession {
        val opts = buildOptions(backend, config)
        return try {
            env.createSession(modelPath, opts)
        } catch (e: Exception) {
            opts.close()
            throw OrtSessionCreationException(backend, e)
        }
    }

    /**
     * Attempts [backend] first; falls back to [OrtBackend.Cpu] on any error.
     * Returns a pair of (session, actualBackend) so callers know what was used.
     */
    fun createWithFallback(
        modelBytes: ByteArray,
        preferredBackend: OrtBackend,
        config: SessionConfig = SessionConfig(),
    ): Pair<OrtSession, OrtBackend> {
        if (preferredBackend == OrtBackend.Cpu) {
            return create(modelBytes, OrtBackend.Cpu, config) to OrtBackend.Cpu
        }
        return try {
            create(modelBytes, preferredBackend, config) to preferredBackend
        } catch (e: OrtSessionCreationException) {
            Log.w(TAG, "Backend ${preferredBackend.displayName} failed, falling back to CPU: ${e.message}")
            create(modelBytes, OrtBackend.Cpu, config) to OrtBackend.Cpu
        }
    }

    // -------------------------------------------------------------------------

    private fun buildOptions(backend: OrtBackend, config: SessionConfig): OrtSession.SessionOptions {
        return OrtSession.SessionOptions().apply {
            setOptimizationLevel(config.graphOptimizationLevel)

            when (backend) {
                is OrtBackend.Cpu -> {
                    setIntraOpNumThreads(config.numThreads)
                }

                is OrtBackend.Xnnpack -> {
                    setIntraOpNumThreads(config.numThreads)
                    addXnnpack(mapOf("intra_op_num_threads" to config.numThreads.toString()))
                }

                is OrtBackend.Nnapi -> {
                    val flags = if (config.nnapiAllowCpuFallback) {
                        backend.flags and NnapiFlags.CPU_DISABLED.inv()
                    } else {
                        backend.flags or NnapiFlags.CPU_DISABLED
                    }
                    addNnapi(mapOf("NNAPIFlags" to flags.toString()))
                    // NNAPI handles its own threading internally; limit ORT CPU threads
                    // to 1 so they don't compete with NNAPI's thread pool.
                    setIntraOpNumThreads(1)
                }
            }
        }
    }
}

class OrtSessionCreationException(
    val backend: OrtBackend,
    cause: Throwable,
) : Exception("Failed to create OrtSession with backend ${backend.displayName}: ${cause.message}", cause)
