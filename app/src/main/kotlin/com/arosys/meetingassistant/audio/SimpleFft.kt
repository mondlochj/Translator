package com.arosys.meetingassistant.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cooley-Tukey radix-2 iterative FFT.
 * Input size must be a power of 2.
 *
 * Operates in-place on separate real and imaginary arrays.
 */
object SimpleFft {

    /** Forward FFT.  Modifies [real] and [imag] in place. */
    fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n and (n - 1) == 0) { "FFT size must be a power of 2, got $n" }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i];  imag[i]  = imag[j];  imag[j]  = tmp
            }
        }

        // Butterfly passes
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angleStep = -2.0 * PI / len
            var i = 0
            while (i < n) {
                var uRe = 1.0; var uIm = 0.0
                for (k in 0 until halfLen) {
                    val idx1 = i + k
                    val idx2 = i + k + halfLen
                    val eRe = real[idx1]; val eIm = imag[idx1]
                    val oRe = real[idx2] * uRe - imag[idx2] * uIm
                    val oIm = real[idx2] * uIm + imag[idx2] * uRe
                    real[idx1] = eRe + oRe; imag[idx1] = eIm + oIm
                    real[idx2] = eRe - oRe; imag[idx2] = eIm - oIm
                    val wRe = cos(angleStep * (k + 1).toDouble())
                    val wIm = sin(angleStep * (k + 1).toDouble())
                    val nextURe = uRe * wRe - uIm * wIm
                    uIm = uRe * wIm + uIm * wRe
                    uRe = nextURe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Computes the magnitude spectrum of [samples] (length must be a power of 2).
     * Returns [samples.size / 2 + 1] bins (one-sided spectrum).
     */
    fun magnitudeSpectrum(samples: DoubleArray): DoubleArray {
        val real = samples.copyOf()
        val imag = DoubleArray(samples.size)
        fft(real, imag)
        val numBins = samples.size / 2 + 1
        return DoubleArray(numBins) { i -> sqrt(real[i] * real[i] + imag[i] * imag[i]) }
    }
}
