package com.arosys.meetingassistant.testing.fakes

import com.arosys.meetingassistant.core.interfaces.ActionItem
import com.arosys.meetingassistant.core.interfaces.MeetingAnalysis
import com.arosys.meetingassistant.core.interfaces.MeetingRecord
import com.arosys.meetingassistant.core.interfaces.SearchResult
import com.arosys.meetingassistant.core.interfaces.StorageProvider
import com.arosys.meetingassistant.core.interfaces.TranscriptEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fully in-memory [StorageProvider] for unit and integration tests.
 *
 * All mutations are reflected immediately in the backing [StateFlow]s, so
 * tests using Turbine can observe live state changes.
 */
class FakeStorageProvider : StorageProvider {

    private var nextMeetingId = 1L
    private var nextEntryId = 1L

    private val meetingsFlow = MutableStateFlow<List<MeetingRecord>>(emptyList())
    private val entries = mutableListOf<TranscriptEntry>()
    private val analyses = mutableMapOf<Long, MeetingAnalysis>()

    // -------------------------------------------------------------------------
    // Meetings
    // -------------------------------------------------------------------------

    override suspend fun createMeeting(title: String, startedAt: Long): Long {
        val id = nextMeetingId++
        meetingsFlow.update { it + MeetingRecord(id, title, startedAt) }
        return id
    }

    override suspend fun finalizeMeeting(meetingId: Long, endedAt: Long) {
        meetingsFlow.update { list ->
            list.map { if (it.id == meetingId) it.copy(endedAt = endedAt) else it }
        }
    }

    override suspend fun getMeeting(meetingId: Long): MeetingRecord? =
        meetingsFlow.value.find { it.id == meetingId }

    override fun observeAllMeetings(): Flow<List<MeetingRecord>> = meetingsFlow

    // -------------------------------------------------------------------------
    // Transcript
    // -------------------------------------------------------------------------

    override suspend fun saveTranscriptEntry(entry: TranscriptEntry): Long {
        val id = nextEntryId++
        val saved = entry.copy(id = id)
        entries += saved
        return id
    }

    override fun observeTranscript(meetingId: Long): Flow<List<TranscriptEntry>> =
        meetingsFlow.map { entries.filter { e -> e.meetingId == meetingId } }

    override suspend fun getTranscript(meetingId: Long): List<TranscriptEntry> =
        entries.filter { it.meetingId == meetingId }

    override suspend fun updateTranslation(entryId: Long, englishText: String) {
        val idx = entries.indexOfFirst { it.id == entryId }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(englishText = englishText)
            // Trigger flow update
            meetingsFlow.update { it }
        }
    }

    // -------------------------------------------------------------------------
    // Analysis
    // -------------------------------------------------------------------------

    override suspend fun saveMeetingAnalysis(analysis: MeetingAnalysis) {
        analyses[analysis.meetingId] = analysis
    }

    override suspend fun getMeetingAnalysis(meetingId: Long): MeetingAnalysis? =
        analyses[meetingId]

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResult> {
        val lowerQuery = query.lowercase()
        return entries
            .filter { it.spanishText.lowercase().contains(lowerQuery) ||
                it.englishText?.lowercase()?.contains(lowerQuery) == true }
            .mapNotNull { entry ->
                val meeting = getMeeting(entry.meetingId) ?: return@mapNotNull null
                SearchResult(
                    meetingId = entry.meetingId,
                    meetingTitle = meeting.title,
                    snippet = entry.englishText ?: entry.spanishText,
                    relevanceScore = 1.0f,
                )
            }
    }

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    fun allMeetings(): List<MeetingRecord> = meetingsFlow.value
    fun allEntries(): List<TranscriptEntry> = entries.toList()
    fun allAnalyses(): Map<Long, MeetingAnalysis> = analyses.toMap()

    fun reset() {
        nextMeetingId = 1L
        nextEntryId = 1L
        meetingsFlow.value = emptyList()
        entries.clear()
        analyses.clear()
    }
}
