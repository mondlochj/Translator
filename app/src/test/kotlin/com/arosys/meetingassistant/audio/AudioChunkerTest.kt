package com.arosys.meetingassistant.audio

import app.cash.turbine.test
import com.arosys.meetingassistant.testing.MainDispatcherRule
import com.arosys.meetingassistant.testing.fixtures.AudioFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudioChunkerTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val chunker = AudioChunker(
        sampleRate = AudioFixtures.SAMPLE_RATE,
        speechThreshold = 0.001f,   // low enough for tone fixture
        silencePadMs = 200,
        maxChunkMs = 5_000,
        preSpeechPadMs = 100,
    )

    @Test
    fun `silent stream produces no chunks`() = runTest {
        val source = AudioFixtures.silentSource()
        // Silence source is infinite — we stop it by reading a limited number of frames
        val limitedSource = object : com.arosys.meetingassistant.core.interfaces.AudioSource by source {
            private var calls = 0
            override fun readFrames(buffer: FloatArray): Int {
                if (calls++ >= 10) return -1
                return source.readFrames(buffer)
            }
        }
        chunker.chunkStream(limitedSource).test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `speech segment followed by silence emits one chunk`() = runTest {
        // 300ms of tone (speech), 600ms of silence (triggers boundary), then EOF
        val source = AudioFixtures.segmentedSource(
            AudioFixtures.tone440Hz(300),
            AudioFixtures.silence(600),
        )
        chunker.chunkStream(source).test {
            val chunk = awaitItem()
            assertTrue("Chunk should contain some samples", chunk.samples.isNotEmpty())
            awaitComplete()
        }
    }

    @Test
    fun `two speech bursts separated by long silence emit two chunks`() = runTest {
        val source = AudioFixtures.segmentedSource(
            AudioFixtures.tone440Hz(300),  // burst 1
            AudioFixtures.silence(500),    // gap > silencePadMs
            AudioFixtures.tone440Hz(300),  // burst 2
            AudioFixtures.silence(500),    // trailing silence
        )
        chunker.chunkStream(source).test {
            val chunk1 = awaitItem()
            val chunk2 = awaitItem()
            assertTrue(chunk1.samples.isNotEmpty())
            assertTrue(chunk2.samples.isNotEmpty())
            assertTrue("Second chunk starts after first",
                chunk2.startTimestampMs >= chunk1.startTimestampMs)
            awaitComplete()
        }
    }

    @Test
    fun `chunk timestamps increase monotonically`() = runTest {
        val source = AudioFixtures.segmentedSource(
            AudioFixtures.tone440Hz(200),
            AudioFixtures.silence(400),
            AudioFixtures.tone440Hz(200),
            AudioFixtures.silence(400),
            AudioFixtures.tone440Hz(200),
            AudioFixtures.silence(400),
        )
        val timestamps = mutableListOf<Long>()
        chunker.chunkStream(source).collect { chunk ->
            timestamps.add(chunk.startTimestampMs)
        }
        for (i in 0 until timestamps.size - 1) {
            assertTrue("Timestamps not increasing: ${timestamps[i]} >= ${timestamps[i+1]}",
                timestamps[i] < timestamps[i + 1])
        }
    }
}
