package com.arosys.meetingassistant.services

import app.cash.turbine.test
import com.arosys.meetingassistant.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [MeetingSession] — the app-scoped bus that connects
 * [TranscriptionService] to [TranslationService].
 *
 * Pure Kotlin coroutine tests; no Android Context required.
 */
class MeetingSessionTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var session: MeetingSession

    @Before
    fun setup() {
        session = MeetingSession()
    }

    // -------------------------------------------------------------------------
    // Meeting ID state
    // -------------------------------------------------------------------------

    @Test
    fun `currentMeetingId starts null`() = runTest {
        assertNull(session.currentMeetingId.value)
    }

    @Test
    fun `setMeetingId updates currentMeetingId`() = runTest {
        session.setMeetingId(42L)
        assertEquals(42L, session.currentMeetingId.value)
    }

    @Test
    fun `setMeetingId can be cleared to null`() = runTest {
        session.setMeetingId(1L)
        session.setMeetingId(null)
        assertNull(session.currentMeetingId.value)
    }

    // -------------------------------------------------------------------------
    // Translation bus
    // -------------------------------------------------------------------------

    @Test
    fun `submitForTranslation emits on pendingTranslations`() = runTest {
        val item = PendingTranslation(entryId = 1L, spanishText = "Hola mundo", meetingId = 10L)

        session.pendingTranslations.test {
            launch { session.submitForTranslation(item) }
            val received = awaitItem()
            assertEquals(item.entryId, received.entryId)
            assertEquals(item.spanishText, received.spanishText)
            assertEquals(item.meetingId, received.meetingId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple submissions are received in order`() = runTest {
        val items = (1L..3L).map { i ->
            PendingTranslation(entryId = i, spanishText = "Sentence $i", meetingId = 100L)
        }

        session.pendingTranslations.test {
            launch {
                items.forEach { session.submitForTranslation(it) }
            }
            for (expected in items) {
                val received = awaitItem()
                assertEquals(expected.entryId, received.entryId)
                assertEquals(expected.spanishText, received.spanishText)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `buffer accepts up to 64 items without suspending emitter`() = runTest {
        // MutableSharedFlow extraBufferCapacity=64 — confirm submissions don't block
        val count = 64
        val items = (1L..count.toLong()).map { i ->
            PendingTranslation(entryId = i, spanishText = "Frase $i", meetingId = 99L)
        }

        // Submit all before collecting — should not hang due to buffer
        launch {
            items.forEach { session.submitForTranslation(it) }
        }

        session.pendingTranslations.test {
            val received = (1..count).map { awaitItem() }
            assertEquals(count, received.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
