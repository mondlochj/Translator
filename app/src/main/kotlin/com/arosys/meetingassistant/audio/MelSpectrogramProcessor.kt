package com.arosys.meetingassistant.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max

/**
 * Computes log-mel spectrograms matching Whisper's exact preprocessing:
 *   n_fft=400, hop_length=160, n_mels=80, sr=16000
 *
 * Output: float32 array of shape [N_MELS × n_frames], in row-major order.
 * For a 30-second clip at 16 kHz, n_frames = 3000 and the full tensor is
 * [80][3000] = 240,000 values.
 *
 * The filterbank is precomputed once at construction and cached.
 */
class MelSpectrogramProcessor {

    companion object {
        const val N_FFT = 400
        const val HOP_LENGTH = 160
        const val N_MELS = 80
        const val SAMPLE_RATE = 16_000
        const val F_MIN = 0.0
        const val F_MAX = 8_000.0
        /** Next power of 2 ≥ N_FFT — used as the FFT size to keep SimpleFft happy. */
        const val FFT_SIZE = 512
        /** Usable frequency bins from FFT_SIZE-point FFT over N_FFT-sample window. */
        const val N_FREQ_BINS = N_FFT / 2 + 1  // 201
    }

    private val hannWindow = DoubleArray(N_FFT) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / (N_FFT - 1)))
    }

    /** Mel filterbank: [N_MELS][N_FREQ_BINS] */
    private val filterbank: Array<DoubleArray> = buildMelFilterbank()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Computes the log-mel spectrogram for [audioSamples] (16 kHz mono float32).
     * Returns a 2D array [N_MELS][n_frames].
     *
     * If [audioSamples] is shorter than N_FFT, returns an empty result.
     */
    fun compute(audioSamples: FloatArray): Array<FloatArray> {
        val padded = reflectPad(audioSamples, N_FFT / 2)
        val nFrames = (padded.size - N_FFT) / HOP_LENGTH + 1
        if (nFrames <= 0) return Array(N_MELS) { FloatArray(0) }

        val melSpec = Array(N_MELS) { FloatArray(nFrames) }

        val frameBuffer = DoubleArray(FFT_SIZE)

        for (t in 0 until nFrames) {
            val start = t * HOP_LENGTH
            // Apply Hann window to the N_FFT-length frame
            frameBuffer.fill(0.0)
            for (i in 0 until N_FFT) {
                frameBuffer[i] = padded[start + i].toDouble() * hannWindow[i]
            }
            // Zero-pad from N_FFT to FFT_SIZE
            // (already zeroed above)

            val mag = SimpleFft.magnitudeSpectrum(frameBuffer)
            // Power spectrum, capped at N_FREQ_BINS
            val power = DoubleArray(N_FREQ_BINS) { i -> mag[i] * mag[i] }

            // Apply mel filterbank
            for (m in 0 until N_MELS) {
                var energy = 0.0
                for (k in 0 until N_FREQ_BINS) energy += filterbank[m][k] * power[k]
                // log mel — Whisper clips at max(1e-10, energy) then takes log10
                melSpec[m][t] = log10(max(1e-10, energy)).toFloat()
            }
        }

        // Whisper normalises: (mel / 4.0).clamp(-1, 0) + 1  — applied globally
        normalise(melSpec)
        return melSpec
    }

    /**
     * Converts [audioSamples] to a flat [FloatArray] suitable as an ORT input
     * tensor of shape [1, N_MELS, n_frames].
     *
     * Pads or truncates to exactly [targetFrames] time steps.
     */
    fun computeFlat(audioSamples: FloatArray, targetFrames: Int = 3000): FloatArray {
        val mel = compute(audioSamples)
        val out = FloatArray(N_MELS * targetFrames)
        for (m in 0 until N_MELS) {
            for (t in 0 until targetFrames) {
                out[m * targetFrames + t] = if (t < mel[m].size) mel[m][t] else -1f
            }
        }
        return out
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun reflectPad(samples: FloatArray, pad: Int): FloatArray {
        val out = FloatArray(samples.size + 2 * pad)
        // Reflect: pad[0..pad) mirrors samples[pad..0)
        for (i in 0 until pad) out[i] = samples[pad - 1 - i]
        samples.copyInto(out, destinationOffset = pad)
        // Reflect: pad at end mirrors last `pad` samples
        val n = samples.size
        for (i in 0 until pad) out[pad + n + i] = samples[n - 2 - i]
        return out
    }

    private fun normalise(mel: Array<FloatArray>) {
        var max = Float.NEGATIVE_INFINITY
        for (row in mel) for (v in row) if (v > max) max = v
        val scale = 1f / 4f
        for (row in mel) for (t in row.indices) {
            row[t] = ((row[t] - max) * scale).coerceIn(-1f, 0f) + 1f
        }
    }

    private fun buildMelFilterbank(): Array<DoubleArray> {
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

        val melMin = hzToMel(F_MIN)
        val melMax = hzToMel(F_MAX)
        // N_MELS + 2 equally-spaced mel points
        val melPoints = DoubleArray(N_MELS + 2) { i -> melMin + i * (melMax - melMin) / (N_MELS + 1) }
        val hzPoints = DoubleArray(N_MELS + 2) { i -> melToHz(melPoints[i]) }
        // Map to FFT bin indices
        val bins = IntArray(N_MELS + 2) { i -> (hzPoints[i] / SAMPLE_RATE * N_FFT).toInt().coerceIn(0, N_FREQ_BINS - 1) }

        val filterbank = Array(N_MELS) { DoubleArray(N_FREQ_BINS) }
        for (m in 0 until N_MELS) {
            val left = bins[m]; val center = bins[m + 1]; val right = bins[m + 2]
            for (k in left until center) {
                if (center > left) filterbank[m][k] = (k - left).toDouble() / (center - left)
            }
            for (k in center until right) {
                if (right > center) filterbank[m][k] = (right - k).toDouble() / (right - center)
            }
        }
        return filterbank
    }
}
