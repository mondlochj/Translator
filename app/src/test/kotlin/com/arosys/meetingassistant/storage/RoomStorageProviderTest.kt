package com.arosys.meetingassistant.storage

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.arosys.meetingassistant.core.interfaces.TranscriptEntry
import com.arosys.meetingassistant.testing.MainDispatcherRule
import com.arosys.meetingassistant.testing.fixtures.TranscriptFixtures
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomStorageProviderTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var provider: RoomStorageProvider

    @Before
    fun setup() {
        db = AppDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        provider = RoomStorageProvider(db)
    }

    @After
    fun teardown() = db.close()

    // -------------------------------------------------------------------------
    // Meetings
    // -------------------------------------------------------------------------

    @Test
    fun `createMeeting returns a positive id`() = runTest {
        val id = provider.createMeeting("Test meeting")
        assert(id > 0)
    }

    @Test
    fun `getMeeting returns the created meeting`() = runTest {
        val id = provider.createMeeting("Standup", startedAt = 1_000L)
        val record = provider.getMeeting(id)
        assertNotNull(record)
        assertEquals("Standup", record!!.title)
        assertEquals(1_000L, record.startedAt)
        assertNull(record.endedAt)
    }

    @Test
    fun `finalizeMeeting sets endedAt`() = runTest {
        val id = provider.createMeeting("Standup")
        provider.finalizeMeeting(id, endedAt = 9_000L)
        assertEquals(9_000L, provider.getMeeting(id)!!.endedAt)
    }

    @Test
    fun `observeAllMeetings emits new meetings in real time`() = runTest {
        provider.observeAllMeetings().test {
            assertEquals(0, awaitItem().size)
            provider.createMeeting("M1")
            assertEquals(1, awaitItem().size)
            provider.createMeeting("M2")
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Transcript
    // -------------------------------------------------------------------------

    @Test
    fun `saveTranscriptEntry persists and is retrievable`() = runTest {
        val meetingId = provider.createMeeting("Test")
        val entry = TranscriptFixtures.scheduleReview(meetingId)
        val savedId = provider.saveTranscriptEntry(entry)
        assert(savedId > 0)

        val retrieved = provider.getTranscript(meetingId)
        assertEquals(1, retrieved.size)
        assertEquals(TranscriptFixtures.SCHEDULE_REVIEW_ES, retrieved.first().spanishText)
    }

    @Test
    fun `getTranscript returns entries in timestamp order`() = runTest {
        val meetingId = provider.createMeeting("Test")
        val entries = TranscriptFixtures.fullMeetingExcerpt(meetingId)
        // Insert in reverse order to verify ordering
        entries.reversed().forEach { provider.saveTranscriptEntry(it) }

        val result = provider.getTranscript(meetingId)
        assertEquals(entries.size, result.size)
        for (i in 0 until result.size - 1) {
            assert(result[i].timestampMs <= result[i + 1].timestampMs)
        }
    }

    @Test
    fun `observeTranscript emits live updates`() = runTest {
        val meetingId = provider.createMeeting("Live test")
        provider.observeTranscript(meetingId).test {
            assertEquals(0, awaitItem().size)
            provider.saveTranscriptEntry(TranscriptFixtures.scheduleReview(meetingId))
            assertEquals(1, awaitItem().size)
            provider.saveTranscriptEntry(TranscriptFixtures.supplierDelay(meetingId))
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    @Test
    fun `search finds entries by Spanish keyword`() = runTest {
        val meetingId = provider.createMeeting("Meeting")
        TranscriptFixtures.fullMeetingExcerpt(meetingId).forEach { provider.saveTranscriptEntry(it) }

        val results = provider.search("cronograma")
        assertEquals(1, results.size)
        assertEquals(meetingId, results.first().meetingId)
    }

    @Test
    fun `search returns empty for unknown keyword`() = runTest {
        val meetingId = provider.createMeeting("Meeting")
        provider.saveTranscriptEntry(TranscriptFixtures.scheduleReview(meetingId))

        val results = provider.search("xyz_nonexistent_xyz")
        assertEquals(0, results.size)
    }

    @Test
    fun `cascade delete removes transcript when meeting deleted`() = runTest {
        val meetingId = provider.createMeeting("To delete")
        provider.saveTranscriptEntry(TranscriptFixtures.scheduleReview(meetingId))
        assertEquals(1, provider.getTranscript(meetingId).size)

        // Delete the meeting directly via DAO
        db.meetingDao().getById(meetingId)?.let {
            db.compileStatement("DELETE FROM meetings WHERE id = $meetingId").execute()
        }
        assertEquals(0, provider.getTranscript(meetingId).size)
    }
}
