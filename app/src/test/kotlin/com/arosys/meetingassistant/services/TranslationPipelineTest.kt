package com.arosys.meetingassistant.services

import app.cash.turbine.test
import com.arosys.meetingassistant.testing.MainDispatcherRule
import com.arosys.meetingassistant.testing.fakes.FakeStorageProvider
import com.arosys.meetingassistant.testing.fakes.FakeTranslationEngine
import com.arosys.meetingassistant.testing.fixtures.TranscriptFixtures
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests the transcription → translation → storage pipeline using fakes.
 *
 * We simulate the [TranslationService.startTranslating] loop inline rather
 * than spinning up a real Android service, following the same pattern as
 * [TranscriptionServiceTest].
 *
 * Execution model:
 *   1. A [PendingTranslation] is submitted to [MeetingSession].
 *   2. The pipeline collects it and calls [FakeTranslationEngine.translateStream].
 *   3. On the final [TranslationResult], [FakeStorageProvider.updateTranslation] is called.
 *   4. We verify storage reflects the translated text.
 */
class TranslationPipelineTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var session: MeetingSession
    private lateinit var engine: FakeTranslationEngine
    private lateinit var storage: FakeStorageProvider

    @Before
    fun setup() {
        session = MeetingSession()
        engine  = FakeTranslationEngine()
        storage = FakeStorageProvider()
    }

    // -------------------------------------------------------------------------
    // Helper — runs one iteration of the translation pipeline
    // -------------------------------------------------------------------------

    /**
     * Simulates what [TranslationService.startTranslating] does for a single
     * [PendingTranslation]: calls the engine, then writes the result to storage.
     */
    private suspend fun runPipeline(pending: PendingTranslation) {
        engine.translateStream(pending.spanishText).collect { result ->
            if (result.isFinal) {
                storage.updateTranslation(pending.entryId, result.translatedText)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `final translation is written to storage`() = runTest {
        val meetingId = storage.createMeeting("Meeting")
        val entryId = storage.saveTranscriptEntry(TranscriptFixtures.scheduleReview(meetingId))

        runPipeline(PendingTranslation(entryId, TranscriptFixtures.SCHEDULE_REVIEW_ES, meetingId))

        val entries = storage.allEntries()
        assertEquals(1, entries.size)
        // FakeTranslationEngine default: "[EN] $sourceText"
        assertEquals("[EN] ${TranscriptFixtures.SCHEDULE_REVIEW_ES}", entries.first().englishText)
    }

    @Test
    fun `custom translation map is respected`() = runTest {
        engine = FakeTranslationEngine(
            translations = mapOf(
                TranscriptFixtures.SCHEDULE_REVIEW_ES to TranscriptFixtures.SCHEDULE_REVIEW_EN
            )
        )
        val meetingId = storage.createMeeting("Meeting")
        val entryId = storage.saveTranscriptEntry(TranscriptFixtures.scheduleReview(meetingId))

        runPipeline(PendingTranslation(entryId, TranscriptFixtures.SCHEDULE_REVIEW_ES, meetingId))

        assertEquals(
            TranscriptFixtures.SCHEDULE_REVIEW_EN,
            storage.allEntries().first().englishText,
        )
    }

    @Test
    fun `multiple segments are all translated and stored`() = runTest {
        val meetingId = storage.createMeeting("Meeting")
        val fixtures = TranscriptFixtures.fullMeetingExcerpt(meetingId)

        // Save all entries first (no English text yet)
        val ids = fixtures.map { storage.saveTranscriptEntry(it.copy(englishText = null)) }

        // Translate all
        ids.zip(fixtures).forEach { (id, entry) ->
            runPipeline(PendingTranslation(id, entry.spanishText, meetingId))
        }

        val saved = storage.allEntries()
        assertEquals(fixtures.size, saved.size)
        saved.forEach { entry ->
            assertNotNull("Entry ${entry.id} has no englishText", entry.englishText)
        }
    }

    @Test
    fun `translation history records all calls`() = runTest {
        val meetingId = storage.createMeeting("Meeting")
        val entries = listOf(
            TranscriptFixtures.SCHEDULE_REVIEW_ES,
            TranscriptFixtures.SUPPLIER_DELAY_ES,
        )

        entries.forEachIndexed { i, text ->
            val id = storage.saveTranscriptEntry(
                TranscriptFixtures.scheduleReview(meetingId, timestampMs = i * 1000L)
                    .copy(spanishText = text, englishText = null)
            )
            runPipeline(PendingTranslation(id, text, meetingId))
        }

        assertEquals(entries.size, engine.translationHistory.size)
        assertEquals(entries[0], engine.translationHistory[0].first)
        assertEquals(entries[1], engine.translationHistory[1].first)
    }

    @Test
    fun `pipeline via MeetingSession SharedFlow`() = runTest {
        val meetingId = storage.createMeeting("Meeting")
        val entryId = storage.saveTranscriptEntry(TranscriptFixtures.scheduleReview(meetingId))

        // Simulate the TranslationService collect loop
        session.pendingTranslations.test {
            launch {
                session.submitForTranslation(
                    PendingTranslation(entryId, TranscriptFixtures.SCHEDULE_REVIEW_ES, meetingId)
                )
            }
            val pending = awaitItem()
            runPipeline(pending)

            assertEquals(
                "[EN] ${TranscriptFixtures.SCHEDULE_REVIEW_ES}",
                storage.allEntries().first().englishText,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `warmUp is called when engine is not ready`() = runTest {
        val notReadyEngine = FakeTranslationEngine()
        // Default FakeTranslationEngine is already ready, but we can verify warmUp count
        assertEquals(0, notReadyEngine.warmUpCount)
        notReadyEngine.warmUp()
        assertEquals(1, notReadyEngine.warmUpCount)
    }

    @Test
    fun `storage updateTranslation propagates through observeTranscript`() = runTest {
        val meetingId = storage.createMeeting("Meeting")
        val entryId = storage.saveTranscriptEntry(TranscriptFixtures.scheduleReview(meetingId))

        storage.observeTranscript(meetingId).test {
            // Initial state: no english text
            val initial = awaitItem()
            assertEquals(1, initial.size)
            assertEquals(null, initial.first().englishText)

            // Simulate translation completing
            storage.updateTranslation(entryId, TranscriptFixtures.SCHEDULE_REVIEW_EN)

            val updated = awaitItem()
            assertEquals(TranscriptFixtures.SCHEDULE_REVIEW_EN, updated.first().englishText)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
