package com.arosys.meetingassistant.audio

import com.arosys.meetingassistant.testing.fixtures.AudioFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MelSpectrogramProcessorTest {

    private val processor = MelSpectrogramProcessor()

    // -------------------------------------------------------------------------
    // Output shape
    // -------------------------------------------------------------------------

    @Test
    fun `compute returns N_MELS rows`() {
        val audio = AudioFixtures.tone440Hz(500)
        val mel = processor.compute(audio)
        assertEquals(MelSpectrogramProcessor.N_MELS, mel.size)
    }

    @Test
    fun `compute returns correct number of frames for 1 second of audio`() {
        val audio = FloatArray(16_000) // exactly 1 second
        val mel = processor.compute(audio)
        // n_frames = (padded_len - N_FFT) / HOP_LENGTH + 1
        // padded_len = 16000 + 2 * 200 = 16400
        // n_frames = (16400 - 400) / 160 + 1 = 101
        assertEquals(101, mel[0].size)
    }

    @Test
    fun `computeFlat pads short audio to targetFrames`() {
        val audio = FloatArray(1600) // 0.1 second
        val flat = processor.computeFlat(audio, targetFrames = 3000)
        assertEquals(MelSpectrogramProcessor.N_MELS * 3000, flat.size)
    }

    @Test
    fun `computeFlat truncates long audio to targetFrames`() {
        val audio = FloatArray(160_000) // 10 seconds
        val flat = processor.computeFlat(audio, targetFrames = 500)
        assertEquals(MelSpectrogramProcessor.N_MELS * 500, flat.size)
    }

    // -------------------------------------------------------------------------
    // Value ranges (after normalisation values should be in [0, 1])
    // -------------------------------------------------------------------------

    @Test
    fun `all mel values after normalisation are in 0 to 1`() {
        val audio = AudioFixtures.whiteNoise(500)
        val mel = processor.compute(audio)
        for (row in mel) {
            for (v in row) {
                assertTrue("Value $v out of [0,1]", v in 0f..1f)
            }
        }
    }

    @Test
    fun `silence produces near-zero mel energy`() {
        val silence = AudioFixtures.silence(500)
        val mel = processor.compute(silence)
        // Silence = minimum energy → normalised values close to 0
        val mean = mel.flatMap { it.asIterable() }.average()
        assertTrue("Expected low energy for silence, got mean=$mean", mean < 0.1)
    }

    @Test
    fun `tone produces higher energy than silence`() {
        val silence = AudioFixtures.silence(500)
        val tone = AudioFixtures.tone440Hz(500)
        val silenceMean = processor.compute(silence).flatMap { it.asIterable() }.average()
        val toneMean = processor.compute(tone).flatMap { it.asIterable() }.average()
        assertTrue("Tone energy ($toneMean) should exceed silence ($silenceMean)",
            toneMean > silenceMean)
    }

    // -------------------------------------------------------------------------
    // FFT correctness (via SimpleFft)
    // -------------------------------------------------------------------------

    @Test
    fun `FFT of pure DC signal has energy only in bin 0`() {
        val n = 512
        val real = DoubleArray(n) { 1.0 }
        val imag = DoubleArray(n)
        SimpleFft.fft(real, imag)
        // Bin 0 should have magnitude n; all others near zero
        val mag0 = kotlin.math.sqrt(real[0] * real[0] + imag[0] * imag[0])
        assertTrue("DC bin magnitude ($mag0) should be ≈ $n", abs(mag0 - n) < 1.0)
        for (k in 1 until n) {
            val mag = kotlin.math.sqrt(real[k] * real[k] + imag[k] * imag[k])
            assertTrue("Non-DC bin $k should be ≈ 0, got $mag", mag < 1e-6)
        }
    }

    @Test
    fun `FFT size must be power of 2`() {
        val thrown = try {
            SimpleFft.fft(DoubleArray(3), DoubleArray(3))
            false
        } catch (e: IllegalArgumentException) {
            true
        }
        assertTrue("Expected IllegalArgumentException for non-power-of-2 input", thrown)
    }
}
