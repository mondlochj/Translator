package com.arosys.meetingassistant.testing.fixtures

import com.arosys.meetingassistant.core.interfaces.AudioSource
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthetic audio sources for unit tests.
 * No real recordings required — tests use signal generators.
 */
object AudioFixtures {

    const val SAMPLE_RATE = 16_000
    const val CHUNK_SIZE = 1_600  // 100 ms at 16 kHz

    /** Silence for [durationMs] milliseconds. */
    fun silence(durationMs: Int = 1_000): FloatArray {
        val samples = (SAMPLE_RATE * durationMs / 1_000)
        return FloatArray(samples)
    }

    /**
     * A 440 Hz sine tone — useful as "speech-like" non-zero audio to confirm
     * that the audio pipeline is passing data through.
     */
    fun tone440Hz(durationMs: Int = 500): FloatArray {
        val samples = (SAMPLE_RATE * durationMs / 1_000)
        return FloatArray(samples) { i ->
            (0.5f * sin(2.0 * PI * 440.0 * i / SAMPLE_RATE)).toFloat()
        }
    }

    /** White noise in [-0.1, 0.1] — simulates ambient room noise. */
    fun whiteNoise(durationMs: Int = 500, seed: Long = 42L): FloatArray {
        val rng = java.util.Random(seed)
        val samples = (SAMPLE_RATE * durationMs / 1_000)
        return FloatArray(samples) { (rng.nextFloat() - 0.5f) * 0.2f }
    }

    // -------------------------------------------------------------------------
    // AudioSource implementations
    // -------------------------------------------------------------------------

    /**
     * Feeds [segments] one at a time into the pipeline.
     * Returns -1 (end-of-stream) after all segments are exhausted.
     */
    fun segmentedSource(vararg segments: FloatArray): AudioSource = SegmentedAudioSource(segments.toList())

    /** Infinite silence source — useful for tests that need to control stream termination manually. */
    fun silentSource(): AudioSource = object : AudioSource {
        override val sampleRateHz = SAMPLE_RATE
        override val channelCount = 1
        override fun readFrames(buffer: FloatArray): Int {
            buffer.fill(0f)
            return buffer.size
        }
    }

    private class SegmentedAudioSource(
        private val segments: List<FloatArray>,
    ) : AudioSource {
        override val sampleRateHz = SAMPLE_RATE
        override val channelCount = 1
        private var index = 0
        private var offset = 0

        override fun readFrames(buffer: FloatArray): Int {
            if (index >= segments.size) return -1
            val segment = segments[index]
            val available = segment.size - offset
            val toRead = minOf(available, buffer.size)
            segment.copyInto(buffer, destinationOffset = 0, startIndex = offset, endIndex = offset + toRead)
            offset += toRead
            if (offset >= segment.size) {
                index++
                offset = 0
            }
            return toRead
        }
    }
}
