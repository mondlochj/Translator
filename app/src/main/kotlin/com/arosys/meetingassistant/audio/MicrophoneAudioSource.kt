package com.arosys.meetingassistant.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.arosys.meetingassistant.core.interfaces.AudioSource

private const val TAG = "MicrophoneAudioSource"
private const val SAMPLE_RATE = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

/**
 * Reads 16 kHz mono PCM from the device microphone via [AudioRecord].
 * Samples are converted to float32 in [-1, 1] as required by Whisper.
 *
 * Call [startRecording] before reading frames, [stopRecording] when done.
 */
class MicrophoneAudioSource : AudioSource {

    override val sampleRateHz = SAMPLE_RATE
    override val channelCount = 1

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ).coerceAtLeast(4096)

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        minBufferSize * 4,
    )

    private val shortBuffer = ShortArray(minBufferSize)

    fun startRecording() {
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized — check RECORD_AUDIO permission")
            return
        }
        audioRecord.startRecording()
        Log.d(TAG, "Recording started (min buffer = $minBufferSize)")
    }

    fun stopRecording() {
        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop()
        }
    }

    /** Returns number of float samples written to [buffer], or -1 on error/stop. */
    override fun readFrames(buffer: FloatArray): Int {
        val toRead = minOf(shortBuffer.size, buffer.size)
        val read = audioRecord.read(shortBuffer, 0, toRead)
        if (read <= 0) return -1
        for (i in 0 until read) {
            buffer[i] = shortBuffer[i] / 32768f
        }
        return read
    }

    fun release() {
        stopRecording()
        audioRecord.release()
    }
}
